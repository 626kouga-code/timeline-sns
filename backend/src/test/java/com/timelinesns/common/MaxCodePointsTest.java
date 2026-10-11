package com.timelinesns.common;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * 文字数の上限をコードポイント単位で数える入力チェック（@MaxCodePoints）。本物の Bean Validation で確かめる。
 */
class MaxCodePointsTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    private record Input(@MaxCodePoints(3) String text) {
    }

    private record Custom(@MaxCodePoints(value = 1, message = "1 文字だけにしてください") String text) {
    }

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static Set<ConstraintViolation<Input>> validate(String text) {
        return validator.validate(new Input(text));
    }

    @Test
    void acceptsUpToLimit() {
        assertThat(validate("")).isEmpty();
        assertThat(validate("abc")).isEmpty();
        assertThat(validate("あいう")).isEmpty();
    }

    @Test
    void rejectsOverLimitWithMessage() {
        assertThat(validate("abcd")).singleElement()
                .extracting(ConstraintViolation::getMessage).isEqualTo("3 文字以内で入力してください");
    }

    @Test
    void countsEmojiAsOneCharacter() {
        // 絵文字は UTF-16 では 2 つの char だが、画面の文字数カウンタと同じく 1 文字と数える
        assertThat("😀😀😀".length()).isEqualTo(6);
        assertThat(validate("😀😀😀")).isEmpty();
        assertThat(validate("😀😀😀😀")).hasSize(1);
    }

    @Test
    void acceptsNull() {
        // 必須かどうかは @NotBlank などで別に確かめる
        assertThat(validate(null)).isEmpty();
    }

    @Test
    void usesCustomMessage() {
        assertThat(validator.validate(new Custom("ab"))).singleElement()
                .extracting(ConstraintViolation::getMessage).isEqualTo("1 文字だけにしてください");
    }
}
