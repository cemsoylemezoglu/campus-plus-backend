package com.campusplus.backend.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PasswordByteLimitValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface PasswordByteLimit {
    String message() default "Parola UTF-8 biçiminde en fazla 72 bayt olmalıdır";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
