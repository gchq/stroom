package stroom.util.shared.validation;

import jakarta.validation.ConstraintValidator;

public interface ValidSimpleCronValidator extends ConstraintValidator<ValidSimpleCron, String> {
    // De-couples the use of the constraint annotation from the implementation of
    // that constraint.
}
