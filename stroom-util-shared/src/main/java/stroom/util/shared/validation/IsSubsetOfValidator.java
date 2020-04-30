package stroom.util.shared.validation;

import javax.validation.ConstraintValidator;
import java.util.List;

public interface IsSubsetOfValidator extends ConstraintValidator<IsSubsetOf, List<String>> {
   // De-couples the use of the constraint annotation from the implementation of
   // that constraint.
}
