package com.timelinesns.like;

/**
 * いいね・取り消しの応答。画面のハートと数をこの値で確定させる。
 *
 * @param liked     操作後に自分がいいねしているか
 * @param likeCount 操作後のいいね数
 */
public record LikeResponse(boolean liked, long likeCount) {
}
