package app.ari.api.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/** Checks {@link MaxBytes}; null values pass, so combine it with {@code @NotBlank} when needed. */
public class MaxBytesValidator implements ConstraintValidator<MaxBytes, CharSequence> {

  private int max;

  @Override
  public void initialize(MaxBytes constraint) {
    this.max = constraint.value();
  }

  @Override
  public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
    return value == null || value.toString().getBytes(StandardCharsets.UTF_8).length <= max;
  }
}
