package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.SequencedCollection;

/**
 * Class to represent the state of the Node File
 * and the associated DocRef.
 * Whether it already exists, and if it does, whether
 * it needs to change its name or move.
 * Used in the V2 Import/Export code.
 */
@NullMarked
class NodeFileDocRefStateV2 {

    /** DocRef defined by contents of .node file */
    private final DocRef nodeFileDocRef;

    /** Stack of docRefs representing the tree above nodeFileDocRef */
    private final Deque<DocRef> nodeFileParentPath;

    /** From ImportState */
    private final boolean useImportFolders;

    /** From ImportState */
    private final boolean useImportNames;

    /** The existing node, if it exists */
    private @Nullable
    final ExplorerNode existingNode;

    /** The existing node path, if the node exists */
    private @Nullable final List<ExplorerNode> existingNodePath;

    /** The existing docRef path, if the node exists */
    private @Nullable final Deque<DocRef> existingDocRefPath;

    /** Whether this DocRef needs moving */
    private final boolean moving;

    /**
     * Constructor.
     * @param explorerNodeService Where to get the node data from.
     * @param nodeFileParentPath  The stack holding the ancestor docRefs for
     *                            this node file.
     * @param nodeFileDocRef      The docRef that we want node state for.
     * @param useImportFolders    From ImportSettings. Use the folders specified in the
     *                            import data structure rather than any existing folders.
     * @param useImportNames      From ImportSettings. Use the item name specified in the
     *                            import data strcture rather than any existing name.
     */
    public NodeFileDocRefStateV2(final ExplorerNodeService explorerNodeService,
                                 final Deque<DocRef> nodeFileParentPath,
                                 final DocRef nodeFileDocRef,
                                 final boolean useImportFolders,
                                 final boolean useImportNames) {

        this.nodeFileDocRef = nodeFileDocRef;
        this.nodeFileParentPath = nodeFileParentPath;
        this.useImportFolders = useImportFolders;
        this.useImportNames = useImportNames;
        final Optional<ExplorerNode> optExistingNode = explorerNodeService.getNode(nodeFileDocRef);

        if (optExistingNode.isPresent()) {
            existingNode = optExistingNode.get();
            existingNodePath = explorerNodeService.getPath(nodeFileDocRef);
            existingDocRefPath = nodePathToDocRefPath(existingNodePath);
            // NB Last assignment due to dependencies!
            moving = !Objects.equals(getDestDocRefPath(), existingDocRefPath);
        } else {
            existingNode = null;
            existingNodePath = null;
            existingDocRefPath = null;
            moving = false;
        }
    }

    /**
     * Holds the code to try to match up any existing node. Tries to find a node matching
     * the UUID of the DocRef. If that fails, and the nodeFileDocRef.getType() is 'Folder'
     * then it tries to find a match based on the name of the folder. This is to support
     * import into systems that have content from V1 import, where the UUID of the
     * import is generated randomly on import so the folders won't match up.
     * @param nodeFileDocRef
     * @return
     */
    private static Optional<ExplorerNode> findExistingNode(final DocRef nodeFileDocRef) {
        return Optional.empty();
    }

    /**
     * Utility to convert explorer node path to docRef path
     */
    private static Deque<DocRef> nodePathToDocRefPath(final SequencedCollection<ExplorerNode> nodePath) {
        final Deque<DocRef> docRefPath = new ArrayDeque<>(nodePath.size());
        for (final ExplorerNode node : nodePath) {
            docRefPath.add(node.getDocRef());
        }

        return docRefPath;
    }

    /**
     * Resolves the path and name to a String path separated with
     * / delimiters.
     * @param docRefPath The path to resolve against
     * @param destName The destination name
     * @return A path in the form /A/B/C/D/name
     */
    private static String resolveToString(final SequencedCollection<DocRef> docRefPath, final String destName) {

        // Convert docRefPath to String
        final StringBuilder buf = new StringBuilder();
        for (final DocRef docRef : docRefPath) {
            buf.append('/');
            buf.append(docRef.getName());
        }

        // Add in the destName
        buf.append('/');
        buf.append(destName);

        return buf.toString();
    }

    /**
     * @return true if the ExplorerNode already exists.
     */
    public boolean nodeAlreadyExists() {
        return existingNode != null;
    }

    /**
     * @return The node path, if the ExplorerNode already exists, or null otherwise.
     */
    public @Nullable List<ExplorerNode> getExistingNodePath() {
        return existingNodePath;
    }

    /**
     * @return The DocRef path, if the ExplorerNode already exists, or null otherwise.
     */
    public Deque<DocRef> getDestDocRefPath() {
        if (useImportFolders || existingDocRefPath == null) {
            return nodeFileParentPath;
        } else {
            return existingDocRefPath;
        }
    }

    /**
     * @return The name of the node to use.
     */
    public String getDestName() {
        if (useImportNames || existingNode == null) {
            return nodeFileDocRef.getName();
        } else {
            return existingNode.getName();
        }
    }

    /**
     * Returns the path to give to ImportState.
     */
    public String getDestPathAsString() {
        return resolveToString(getDestDocRefPath(), getDestName());
    }

    /**
     * @return true if the existing object must be moved,
     * false if it doesn't already exist or is not moving.
     */
    public boolean isMoving() {
        return moving;
    }

    /**
     * @return Returns true if the type specified in the node file is
     * 'Folder'. Other types of folder result in false returned.
     */
    public boolean isNodeFileExactlyFolderType() {
        return ExplorerConstants.FOLDER_TYPE.equals(nodeFileDocRef.getType());
    }

    /**
     * Returns the DocRef of the parent of the current thing we're importing.
     * Note that the parent must exist (IOException) and it must be some
     * kind of Folder thing (SYSTEM, GitRepo or Folder) (IOException).
     * @return The last element in the destPath. Must be a Folder type.
     * @throws IOException if the destPath is empty or the last element
     * is not a Folder type.
     */
    public DocRef getDestParentDocRef() throws IOException {
        final Deque<DocRef> destPath = getDestDocRefPath();

        // The parent DocRef - this should be some kind of Folder
        final DocRef parentDocRef;

        if (destPath.isEmpty()) {
            // Shouldn't happen - error somewhere in the code
            throw new IOException("No parent for this node");
        } else {
            parentDocRef = destPath.getLast();
        }

        if (!(parentDocRef.equals(ExplorerConstants.SYSTEM_DOC_REF)
              || ExplorerConstants.isFolder(parentDocRef))) {
            // Error somewhere in the import structure
            throw new IOException("Error: parent node '" + parentDocRef.getName()
                                  + "' is not a Folder; instead it is a '" + parentDocRef.getType() +"'");
        }

        return parentDocRef;
    }

}
