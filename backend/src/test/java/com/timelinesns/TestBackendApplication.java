package com.timelinesns;

import org.springframework.boot.SpringApplication;

/**
 * Testcontainers の PostgreSQL につないでアプリを起動する（docker-compose なしで動かしたいとき用）。
 */
public final class TestBackendApplication {

    private TestBackendApplication() {
    }

    public static void main(String[] args) {
        SpringApplication.from(BackendApplication::main).with(TestcontainersConfiguration.class).run(args);
    }
}
