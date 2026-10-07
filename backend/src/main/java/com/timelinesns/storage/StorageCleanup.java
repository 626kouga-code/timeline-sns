package com.timelinesns.storage;

import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * DB の更新と保存先（S3）のファイルを食い違わせないための後片付け。トランザクションの中で呼ぶ。
 * <ul>
 *   <li>{@link #deleteAfterCommit}: 使われなくなるファイル（差し替え前の画像、削除した投稿の画像）。
 *       DB の更新が確定してから消す。取り消されたら残す</li>
 *   <li>{@link #deleteOnRollback}: 先に保存した新しいファイル。DB の更新が取り消されたら消す</li>
 * </ul>
 */
@Component
public class StorageCleanup {

    private final ObjectStorage storage;

    public StorageCleanup(ObjectStorage storage) {
        this.storage = storage;
    }

    public void deleteAfterCommit(List<String> keys) {
        register(keys, TransactionSynchronization.STATUS_COMMITTED);
    }

    public void deleteOnRollback(List<String> keys) {
        register(keys, TransactionSynchronization.STATUS_ROLLED_BACK);
    }

    private void register(List<String> keys, int whenStatus) {
        if (keys.isEmpty()) {
            return;
        }
        List<String> copy = List.copyOf(keys);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == whenStatus) {
                    copy.forEach(storage::deleteQuietly);
                }
            }
        });
    }
}
