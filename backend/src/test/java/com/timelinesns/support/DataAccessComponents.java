package com.timelinesns.support;

import com.timelinesns.comment.CommentQuery;
import com.timelinesns.notification.NotificationQuery;
import com.timelinesns.post.PostQuery;
import com.timelinesns.storage.MediaUrls;
import com.timelinesns.storage.ObjectStorage;
import com.timelinesns.storage.S3Config;
import com.timelinesns.storage.StorageCleanup;
import com.timelinesns.storage.StorageProperties;
import com.timelinesns.user.UserQuery;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;

/**
 * {@link DataAccessTest} で読み込むクラス。@DataJpaTest は JPA のリポジトリしか読み込まないので、
 * SQL を書いたクラスと画像の保存先をここで足す。テストごとに変えるとコンテキストを使い回せなくなるので、全部まとめておく。
 */
@TestConfiguration(proxyBeanMethods = false)
// スライステストでは @ConfigurationPropertiesScan が効かないので、使う設定だけ読み込む
@EnableConfigurationProperties(StorageProperties.class)
@Import({UserQuery.class, PostQuery.class, CommentQuery.class, NotificationQuery.class, MediaUrls.class,
        S3Config.class, ObjectStorage.class, StorageCleanup.class})
public class DataAccessComponents {
}
