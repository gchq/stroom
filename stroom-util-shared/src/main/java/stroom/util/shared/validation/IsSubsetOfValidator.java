package stroom.util.shared.validation;

import java.util.List;
import javax.validation.ConstraintValidator;

public interface IsSubsetOfValidator extends ConstraintValidator<IsSubsetOf, List<String>> {
    // De-couples the use of the constraint annotation from the implementation of
    // that constraint.
}
