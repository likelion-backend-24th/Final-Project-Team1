package com.team1.reservation.config;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 커밋된 뒤에만 실행돼야 하는 부수효과를 걸어 둔다. 트랜잭션이 없으면 즉시 실행한다. */
@Component
public class AfterCommitExecutor {

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
