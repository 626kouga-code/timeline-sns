package com.timelinesns.post;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.timelinesns.common.ApiException;
import com.timelinesns.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

/**
 * 投稿の作成・取得・削除のロジック（PostService）。DB と画像の保存はモックにし、判断と呼び出しの順序を確かめる。
 */
class PostServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");
    private static final UUID ME = UUID.randomUUID();
    private static final UUID POST_ID = UUID.randomUUID();

    private PostRepository posts;
    private PostQuery query;
    private PostImageStore images;
    private UserRepository users;
    private PostService service;
    private PostResponse response;

    @BeforeEach
    void setUp() {
        posts = mock(PostRepository.class);
        query = mock(PostQuery.class);
        images = mock(PostImageStore.class);
        users = mock(UserRepository.class);
        service = new PostService(posts, query, images, users, Clock.fixed(NOW, ZoneOffset.UTC));
        when(users.existsById(ME)).thenReturn(true);
        when(posts.saveAndFlush(any())).thenAnswer(invocation -> {
            Post post = invocation.getArgument(0);
            ReflectionTestUtils.setField(post, "id", POST_ID);
            return post;
        });
        response = new PostResponse(POST_ID, "本文", List.of(), NOW,
                new PostResponse.Author(ME, "taro", "太郎", null), 0, 0, false);
        when(query.findVisible(POST_ID, ME)).thenReturn(Optional.of(response));
    }

    private static Post post(UUID author) {
        Post post = new Post(author, "本文", NOW);
        ReflectionTestUtils.setField(post, "id", POST_ID);
        return post;
    }

    private static void assertApiError(Runnable call, HttpStatus status, String code) {
        assertThatThrownBy(call::run).isInstanceOfSatisfying(ApiException.class, e -> {
            assertThat(e.getStatus()).isEqualTo(status);
            assertThat(e.getCode()).isEqualTo(code);
        });
    }

    @Nested
    class Create {

        @Test
        void savesStrippedBodyAndReturnsCreatedPost() {
            PostResponse created = service.create(ME, new CreatePostRequest("  こんにちは  ", null));

            ArgumentCaptor<Post> saved = ArgumentCaptor.forClass(Post.class);
            verify(posts).saveAndFlush(saved.capture());
            assertThat(saved.getValue().getBody()).isEqualTo("こんにちは");
            assertThat(saved.getValue().getUserId()).isEqualTo(ME);
            assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
            assertThat(created).isEqualTo(response);
        }

        @Test
        void preparesImagesBeforeSavingAnything() {
            MultipartFile file = new MockMultipartFile("images", "a.png", "image/png", new byte[] {1});
            List<PostImageStore.Prepared> prepared = List.of();
            when(images.prepare(List.of(file))).thenReturn(prepared);

            service.create(ME, new CreatePostRequest("", List.of(file)));

            // 形式違反などは保存前に弾くので、作り直し → 投稿の保存 → 画像の保存 の順
            InOrder order = inOrder(images, posts);
            order.verify(images).prepare(List.of(file));
            order.verify(posts).saveAndFlush(any());
            order.verify(images).save(POST_ID, prepared);
        }

        @Test
        void rejectsEmptyBodyWithoutImages() {
            assertApiError(() -> service.create(ME, new CreatePostRequest("   ", null)),
                    HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
            verifyNoInteractions(posts, images);
        }

        @Test
        void ignoresEmptyFileParts() {
            MultipartFile empty = new MockMultipartFile("images", "", "application/octet-stream", new byte[0]);

            assertApiError(() -> service.create(ME, new CreatePostRequest(null, List.of(empty))),
                    HttpStatus.BAD_REQUEST, "VALIDATION_FAILED");
        }

        @Test
        void deletedUserIsUnauthorized() {
            assertApiError(() -> service.create(UUID.randomUUID(), new CreatePostRequest("こんにちは", null)),
                    HttpStatus.UNAUTHORIZED, "USER_NOT_FOUND");
            verifyNoInteractions(posts);
        }
    }

    @Test
    void getReturnsVisiblePostOrNotFound() {
        assertThat(service.get(POST_ID, ME)).isEqualTo(response);
        assertApiError(() -> service.get(UUID.randomUUID(), ME), HttpStatus.NOT_FOUND, "POST_NOT_FOUND");
    }

    @Nested
    class Delete {

        @Test
        void ownerDeletesPostAndSchedulesImageDeletion() {
            Post mine = post(ME);
            when(posts.findById(POST_ID)).thenReturn(Optional.of(mine));

            service.delete(POST_ID, ME);

            verify(images).deleteAfterCommit(POST_ID);
            verify(posts).delete(mine);
        }

        @Test
        void cannotDeleteOthersPost() {
            when(posts.findById(POST_ID)).thenReturn(Optional.of(post(UUID.randomUUID())));

            assertApiError(() -> service.delete(POST_ID, ME), HttpStatus.FORBIDDEN, "FORBIDDEN");
            verify(posts, never()).delete(any());
            verify(images, never()).deleteAfterCommit(any());
        }

        @Test
        void unknownPostIsNotFound() {
            assertApiError(() -> service.delete(POST_ID, ME), HttpStatus.NOT_FOUND, "POST_NOT_FOUND");
        }
    }
}
