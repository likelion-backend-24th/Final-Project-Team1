package com.team1.ai;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 트랜잭션 커밋 직후에 태스크를 실행한다. 트랜잭션이 없는 컨텍스트에서는 즉시 실행.
 * LLM 호출처럼 외부 I/O가 필요한 비동기 작업을 커밋 후로 미룰 때 사용한다.
 */
@Component
public class AfterCommitRunner {

    public void execute(Runnable task) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            task.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                task.run();
            }
        });
    }
}
