package com.rainbutler.domain.user.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 닉네임 공통 제약입니다. 공백만 있으면 안 되고 {@link NicknamePolicy}의 길이(2~20자)여야 합니다.
 *
 * <p>앞뒤 공백 제거는 요청 DTO에서 {@link NicknamePolicy#normalize}로 먼저 합니다.
 */
@NotBlank
@Size(min = NicknamePolicy.MIN_LENGTH, max = NicknamePolicy.MAX_LENGTH)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD,
        ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = {})
@Documented
public @interface ValidNickname {

    String message() default "닉네임은 2~20자로 입력해 주세요.";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
