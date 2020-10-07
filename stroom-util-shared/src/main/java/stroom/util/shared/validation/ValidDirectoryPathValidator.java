package stroom.util.shared.validation;

import javax.validation.ConstraintValidator;

public interface ValidDirectoryPathValidator extends ConstraintValidator<ValidDirectoryPath, String> {
   // De-couples the use of the constraint annotation from the implementation of
   // that constraint.
}
