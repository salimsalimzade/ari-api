package app.ari.api.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The text must fit in {@link #value()} bytes as UTF-8. {@code @Size} counts characters, which is
 * not enough for limits measured in bytes, like bcrypt's 72-byte password limit.
 */
@Documented
@Constraint(validatedBy = MaxBytesValidator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface MaxBytes {

  int value();

  String message() default "must be at most {value} bytes";

  Class<?>[] groups() default {};

  Class<? extends Payload>[] payload() default {};
}
