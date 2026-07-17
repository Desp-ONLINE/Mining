package org.desp.mining.database;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import org.desp.mining.Mining;

/**
 * Mongo 영구 저장 전용 실행기.
 *
 * Bukkit 비동기 스케줄러를 쓰지 않는 이유 (롤백 사고의 직접 원인):
 * - 서버 종료 시 "킥 → PlayerQuitEvent → 플러그인 disable" 순서라, 퇴장 이벤트에서
 *   runTaskAsynchronously 로 예약한 Mongo 저장은 다음 틱이 없어 디스패치되지 못하고
 *   disable 의 cancelTasks 로 전부 취소된다. → 재시작 때마다 전원 퇴장 저장 유실.
 * - 스레드 풀이라 저장 순서가 역전될 수 있다. (재시도로 늦어진 옛 퇴장 저장이
 *   빠른 재접속 세션의 새 저장 "뒤"에 도착하면 Mongo 가 구본으로 남는다)
 *
 * 단일 스레드 FIFO 큐라 제출 순서 = 반영 순서가 보장되고, onDisable 에서 drain 하므로
 * 종료 중에 제출된 저장도 반드시 완료된다.
 */
public final class MongoSaveExecutor {

    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "Mining-MongoSave");
        thread.setDaemon(false);
        return thread;
    });

    private MongoSaveExecutor() {
    }

    /** 저장 작업 제출. 이미 종료된 뒤라면 호출한 스레드에서 그 자리에서 실행한다. */
    public static void submit(Runnable task) {
        try {
            EXECUTOR.execute(task);
        } catch (RejectedExecutionException e) {
            task.run();
        }
    }

    /** onDisable 에서 호출: 큐에 남은 저장을 모두 끝낼 때까지 기다린다. */
    public static void shutdownAndDrain() {
        EXECUTOR.shutdown();
        try {
            if (!EXECUTOR.awaitTermination(20, TimeUnit.SECONDS)) {
                Mining.getInstance().getLogger().severe(
                        "[채광] 종료 대기 20초 안에 Mongo 저장 큐를 비우지 못했습니다. 일부 저장이 유실됐을 수 있습니다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
