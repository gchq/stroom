package stroom.util.shared.validation;

import java.util.Collection;
import javax.validation.ConstraintValidator;

public interface IsSupersetOfValidator extends ConstraintValidator<IsSupersetOf, Collection<String>> {
    // De-couples the use of the constraint annotation from the implementation of
    // that constraint.
}
