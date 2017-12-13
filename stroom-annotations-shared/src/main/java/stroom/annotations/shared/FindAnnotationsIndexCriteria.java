package stroom.annotations.shared;

import stroom.entity.shared.FindDocumentEntityCriteria;

public class FindAnnotationsIndexCriteria extends FindDocumentEntityCriteria {
    public FindAnnotationsIndexCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindAnnotationsIndexCriteria(final String name) {
        super(name);
    }
}
