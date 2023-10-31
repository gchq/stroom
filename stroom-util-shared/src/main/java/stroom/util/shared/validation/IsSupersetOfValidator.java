package stroom.util.shared.validation;

import jakarta.validation.ConstraintValidator;

import java.util.Collection;

public interface IsSupersetOfValidator extends ConstraintValidator<IsSupersetOf, Collection<String>> {
    // De-couples the use of the constraint annotation from the implementation of
    // that constraint.
}
