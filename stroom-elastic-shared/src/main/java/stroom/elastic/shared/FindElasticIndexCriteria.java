package stroom.elastic.shared;

import stroom.entity.shared.FindDocumentEntityCriteria;

public class FindElasticIndexCriteria extends FindDocumentEntityCriteria {
    public FindElasticIndexCriteria() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindElasticIndexCriteria(final String name) {
        super(name);
    }
}
