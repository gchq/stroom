package stroom.util.shared;

import javax.validation.ConstraintValidator;

public interface ValidRegexValidator extends ConstraintValidator<ValidRegex, String> {
   // De-couples the use of the constraint annotation from the implementation of
   // that constraint.
}
