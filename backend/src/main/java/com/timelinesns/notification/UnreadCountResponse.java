package com.timelinesns.notification;

/**
 * 未読の通知の数（F-51）。100 件で打ち切る。
 */
public record UnreadCountResponse(long count) {
}
