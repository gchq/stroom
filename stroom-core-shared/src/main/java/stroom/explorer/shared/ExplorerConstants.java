package stroom.explorer.shared;

import stroom.query.api.v2.DocRef;

public final class ExplorerConstants {
    public static final String SYSTEM = "System";
    public static final String FOLDER = "Folder";
    public static final DocRef ROOT_DOC_REF = new DocRef(SYSTEM, "0",SYSTEM);
    public static final String ANNOTATIONS = "Annotations";
    public static final DocRef ANNOTATIONS_DOC_REF = new DocRef(ANNOTATIONS, "1", ANNOTATIONS);

    public static final String ELASTIC_SEARCH = "ElasticSearch";
    public static final DocRef ELASTIC_SEARCH_DOC_REF = new DocRef(ELASTIC_SEARCH, "2", ELASTIC_SEARCH);

    private ExplorerConstants() {
    }
}
