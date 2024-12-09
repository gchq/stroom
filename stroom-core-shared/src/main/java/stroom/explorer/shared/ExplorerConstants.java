package stroom.explorer.shared;

import stroom.docref.DocRef;
import stroom.svg.shared.SvgImage;
import stroom.util.shared.GwtNullSafe;

import java.util.Objects;

public final class ExplorerConstants {

    public static final String ALL_CREATE_PERMISSIONS = "[ all ]";
    public static final String FOLDER_TYPE = "Folder";

    public static final String SYSTEM = "System";
    public static final String SYSTEM_TYPE = SYSTEM;
    public static final DocRef SYSTEM_DOC_REF = new DocRef(SYSTEM_TYPE, "0", SYSTEM);
    public static final ExplorerNode SYSTEM_NODE = ExplorerNode.builder()
            .docRef(SYSTEM_DOC_REF)
            .rootNodeUuid(SYSTEM_DOC_REF.getUuid())
            .icon(SvgImage.DOCUMENT_SYSTEM)
            .build();

    public static final String FAVOURITES = "Favourites";
    public static final String FAVOURITES_TYPE = FAVOURITES;
    public static final DocRef FAVOURITES_DOC_REF = new DocRef(FAVOURITES_TYPE, "1", FAVOURITES);
    public static final ExplorerNode FAVOURITES_NODE = ExplorerNode.builder()
            .docRef(FAVOURITES_DOC_REF)
            .rootNodeUuid(FAVOURITES_DOC_REF.getUuid())
            .icon(SvgImage.FAVOURITES)
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

    public static boolean isSystemNode(final String type,
                                       final String uuid) {
        return Objects.equals(SYSTEM_NODE.getType(), type)
               && Objects.equals(SYSTEM_NODE.getUuid(), uuid);
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

    public static boolean isFavouritesNode(final String type,
                                           final String uuid) {
        return Objects.equals(SYSTEM_NODE.getType(), type)
               && Objects.equals(SYSTEM_NODE.getUuid(), uuid);
    }

    /**
     * @return True if node is non-null and one of the root nodes
     */
    public static boolean isRootNode(final ExplorerNode node) {
        if (node == null) {
            return false;
        } else {
            return Objects.equals(SYSTEM_NODE, node)
                   || Objects.equals(FAVOURITES_NODE, node);
        }
    }

    /**
     * Tests whether a node is a folder
     */
    public static boolean isFolder(final ExplorerNode node) {
        return GwtNullSafe.test(node,
                ExplorerNode::getDocRef,
                docRef -> FOLDER_TYPE.equals(docRef.getType()));
    }

    /**
     * Tests whether a {@link DocRef} is a folder
     */
    public static boolean isFolder(final DocRef docRef) {
        return docRef != null && FOLDER_TYPE.equals(docRef.getType());
    }

    /**
     * Tests whether a {@link DocRef} is a folder or the system node
     */
    public static boolean isFolderOrSystem(final DocRef docRef) {
        return docRef != null && (FOLDER_TYPE.equals(docRef.getType()) || Objects.equals(SYSTEM_DOC_REF, docRef));
    }
}
