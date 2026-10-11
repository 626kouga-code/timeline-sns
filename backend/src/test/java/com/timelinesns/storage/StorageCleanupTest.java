package com.timelinesns.storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.timelinesns.support.DataAccessTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * DB の更新の確定・取り消しに合わせた、保存先（S3Mock）のファイルの後片付け（StorageCleanup）。
 * 実際にトランザクションをコミット・ロールバックして確かめるので、テスト自体はトランザクションの外で動かす。
 */
@DataAccessTest
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class StorageCleanupTest {

    @Autowired
    private StorageCleanup cleanup;

    @Autowired
    private ObjectStorage storage;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate tx;
    private String key;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        key = "test/" + UUID.randomUUID() + ".jpg";
        storage.put(key, new byte[] {1}, "image/jpeg");
    }

    @Test
    void deleteAfterCommitDeletesWhenCommitted() {
        tx.executeWithoutResult(status -> {
            cleanup.deleteAfterCommit(List.of(key));
            // コミットするまでは消さない
            assertThat(storage.get(key)).isPresent();
        });

        assertThat(storage.get(key)).isEmpty();
    }

    @Test
    void deleteAfterCommitKeepsFileWhenRolledBack() {
        tx.executeWithoutResult(status -> {
            cleanup.deleteAfterCommit(List.of(key));
            status.setRollbackOnly();
        });

        assertThat(storage.get(key)).isPresent();
    }

    @Test
    void deleteOnRollbackDeletesWhenRolledBack() {
        tx.executeWithoutResult(status -> {
            cleanup.deleteOnRollback(List.of(key));
            status.setRollbackOnly();
        });

        assertThat(storage.get(key)).isEmpty();
    }

    @Test
    void deleteOnRollbackKeepsFileWhenCommitted() {
        tx.executeWithoutResult(status -> cleanup.deleteOnRollback(List.of(key)));

        assertThat(storage.get(key)).isPresent();
    }
}
