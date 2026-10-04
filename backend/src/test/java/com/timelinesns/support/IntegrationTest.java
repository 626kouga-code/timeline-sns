package com.timelinesns.support;

import com.timelinesns.TestcontainersConfiguration;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 結合テスト（Testcontainers の PostgreSQL + MockMvc）。
 * すべての結合テストで同じ設定にし、Spring のコンテキストとコンテナを使い回す。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, QueryCounter.class})
@ActiveProfiles("test")
public @interface IntegrationTest {
}
