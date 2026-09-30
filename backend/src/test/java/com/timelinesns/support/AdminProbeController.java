package com.timelinesns.support;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理者向け API の認可ルール（/api/admin/**）を確かめるためのテスト専用エンドポイント。
 * 管理 API（#32）を実装したら、そちらのテストに置き換えて削除する。
 */
@RestController
public class AdminProbeController {

    @GetMapping("/api/admin/probe")
    public String probe() {
        return "ok";
    }
}
