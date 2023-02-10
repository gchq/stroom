package stroom.explorer.shared;

import stroom.docref.DocRef;

import java.util.Objects;

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
    public static final ExplorerNode FAVOURITES_NODE = ExplorerNode.builder()
            .docRef(FAVOURITES_DOC_REF)
            .rootNodeUuid(FAVOURITES_DOC_REF.getUuid())
            .build();

    private ExplorerConstants() {
    }

    /**
     * Tests whether a node is the root System node
     */
    public static boolean isSystemNode(final ExplorerNode node) {
        if (node == null) {
            return false;
        } else {
            return Objects.equals(SYSTEM_NODE, node);
        }
    }

    /**
     * Tests whether a node is the root Favourites node
     */
    public static boolean isFavouritesNode(final ExplorerNode node) {
        if (node == null) {
            return false;
        } else {
            return Objects.equals(FAVOURITES_NODE, node);
        }
    }
}
