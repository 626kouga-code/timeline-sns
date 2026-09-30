package com.timelinesns.common;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 文字数の上限をコードポイント単位で確認する。{@code @Size} は UTF-16 の char 単位で数えるため、
 * 絵文字などが 2 文字と数えられてしまう。画面の文字数カウンタと数え方を揃えるために使う。
 */
@Documented
@Constraint(validatedBy = MaxCodePoints.Validator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxCodePoints {

    int value();

    String message() default "{value} 文字以内で入力してください";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<MaxCodePoints, String> {

        private int max;

        @Override
        public void initialize(MaxCodePoints annotation) {
            this.max = annotation.value();
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.codePointCount(0, value.length()) <= max;
        }
    }
}
