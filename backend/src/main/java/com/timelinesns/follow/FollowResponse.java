package com.timelinesns.follow;

/**
 * フォロー・解除の応答。画面のボタンとフォロワー数をこの値で確定させる。
 *
 * @param following     操作後に自分が相手をフォローしているか
 * @param followerCount 操作後の相手のフォロワー数
 */
public record FollowResponse(boolean following, long followerCount) {
}
