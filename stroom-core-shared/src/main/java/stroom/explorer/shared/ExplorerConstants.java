package stroom.explorer.shared;

import stroom.query.api.v2.DocRef;

public final class ExplorerConstants {
    public static final String SYSTEM = "System";
    public static final String FOLDER = "Folder";
    public static final DocRef ROOT_DOC_REF = new DocRef.Builder()
            .type(SYSTEM)
            .uuid("0")
            .name(SYSTEM)
            .build();
    public static final String ANNOTATIONS = "Annotations";
    public static final DocRef ANNOTATIONS_DOC_REF = new DocRef.Builder()
            .type(ANNOTATIONS)
            .uuid("1")
            .name(ANNOTATIONS)
            .build();

    private ExplorerConstants() {
    }
}
