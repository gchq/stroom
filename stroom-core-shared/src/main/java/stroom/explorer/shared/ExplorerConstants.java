package stroom.explorer.shared;

import stroom.docref.DocRef;

public final class ExplorerConstants {

    public static final String SYSTEM = "System";
    public static final String FAVOURITES = "Favourites";
    public static final String FOLDER = "Folder";

    public static final DocRef SYSTEM_DOC_REF = new DocRef(SYSTEM, "0", SYSTEM);
    public static final ExplorerNode SYSTEM_NODE = ExplorerNode.builder()
            .docRef(SYSTEM_DOC_REF)
            .rootNodeUuid(SYSTEM_DOC_REF.getUuid())
            .build();
    public static final DocRef FAVOURITES_DOC_REF = new DocRef(FAVOURITES, "1", FAVOURITES);

    private ExplorerConstants() {
    }
}
