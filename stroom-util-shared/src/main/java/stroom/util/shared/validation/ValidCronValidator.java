package stroom.util.shared.validation;

import javax.validation.ConstraintValidator;

public interface ValidCronValidator extends ConstraintValidator<ValidCron, String> {
   // De-couples the use of the constraint annotation from the implementation of
   // that constraint.
}
