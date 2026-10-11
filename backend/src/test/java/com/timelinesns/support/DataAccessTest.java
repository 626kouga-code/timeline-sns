package com.timelinesns.support;

import com.timelinesns.TestcontainersConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * データアクセス層のテスト（Testcontainers の PostgreSQL・S3Mock と、実際にデータをやり取りする）。
 * Web 層やサービスは読み込まず、JPA のリポジトリと SQL を書いたクラス（{@link DataAccessComponents}）だけを使う。
 *
 * <p>各テストはトランザクションの中で動き、終わるとロールバックする（DB に入れたデータは残らない）。
 * スキーマは本番と同じ Flyway のマイグレーションで作る。すべてのテストで同じ設定にし、コンテナを使い回す。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@DataJpaTest
// 組み込み DB に置き換えず、Testcontainers の PostgreSQL を使う
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, DataAccessComponents.class})
@ActiveProfiles("test")
public @interface DataAccessTest {
}
