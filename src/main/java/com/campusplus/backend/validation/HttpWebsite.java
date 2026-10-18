package com.campusplus.backend.validation;

import jakarta.validation.*;
import java.lang.annotation.*;

@Documented @Constraint(validatedBy = HttpWebsiteValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpWebsite {
    String message() default "Geçerli bir HTTP/HTTPS web adresi giriniz";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
