package com.timelinesns.support;

import javax.sql.DataSource;
import net.ttddyy.dsproxy.QueryCountHolder;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 実行された SQL の数を数える（N+1 の検出用）。DataSource を datasource-proxy で包む。
 * MockMvc はテストと同じスレッドで動くので、スレッドごとの集計（{@link QueryCountHolder}）で数えられる。
 */
@TestConfiguration(proxyBeanMethods = false)
public class QueryCounter {

    @Bean
    static BeanPostProcessor countingDataSource() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource dataSource) {
                    return ProxyDataSourceBuilder.create(dataSource).name(beanName).countQuery().build();
                }
                return bean;
            }
        };
    }

    /**
     * {@code action} の中で実行された SQL の数を返す。
     */
    public static long count(ThrowingRunnable action) throws Exception {
        QueryCountHolder.clear();
        action.run();
        long total = QueryCountHolder.getGrandTotal().getTotal();
        QueryCountHolder.clear();
        return total;
    }

    /**
     * 例外を投げられる処理（MockMvc の perform など）。
     */
    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
