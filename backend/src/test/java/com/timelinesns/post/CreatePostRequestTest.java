package com.timelinesns.post;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

/**
 * 投稿作成の入力の整形（CreatePostRequest）。
 */
class CreatePostRequestTest {

    @Test
    void strippedBodyTreatsMissingBodyAsEmpty() {
        assertThat(new CreatePostRequest(null, null).strippedBody()).isEmpty();
        assertThat(new CreatePostRequest("  こんにちは \n", null).strippedBody()).isEqualTo("こんにちは");
    }

    @Test
    void filesSkipsEmptyParts() {
        MultipartFile image = new MockMultipartFile("images", "a.png", "image/png", new byte[] {1});
        MultipartFile empty = new MockMultipartFile("images", "", "application/octet-stream", new byte[0]);

        assertThat(new CreatePostRequest(null, List.of(empty, image, empty)).files()).containsExactly(image);
        assertThat(new CreatePostRequest(null, null).files()).isEmpty();
    }
}
