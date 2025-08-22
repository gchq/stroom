package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

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

    /** DocRef that represents the parent of the nodeFileDocRef in the import structure */
    private final DocRef importParentDocRef;

    /** DocRef that represents the existing item, or null if there isn't one */
    private @Nullable final DocRef existingDocRef;

    /** The existing node, if it exists */
    private @Nullable final ExplorerNode existingNode;

    /** The node that is the parent of the existing item in Stroom */
    private @Nullable final ExplorerNode existingParentNode;

    /** The destination parent node; either import or existing */
    private final ExplorerNode destParentNode;

    /** Stack of docRefs representing the tree above nodeFileDocRef */
    //private final Deque<DocRef> nodeFileParentPath;

    /** Whether we need to replace the UUID in the current item? */
    private final boolean replaceUuid;

    /** Whether this DocRef needs renaming */
    private final boolean rename;

    /** Whether this DocRef needs moving */
    private final boolean moving;

    /** Path to return for ImportState records */
    private final String pathAsString;

    /** Logger */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(NodeFileDocRefStateV2.class);

    /**
     * Constructor.
     * @param explorerNodeService Where to get the node data from.
     * @param importParentPath    The stack holding the ancestor docRefs for
     *                            this node file.
     * @param nodeFileDocRef      The docRef that we want node state for, from the .node file.
     * @param useImportFolders    From ImportSettings. Use the folders specified in the
     *                            import data structure rather than any existing folders.
     * @param useImportNames      From ImportSettings. Use the item name specified in the
     *                            import data strcture rather than any existing name.
     */
    public NodeFileDocRefStateV2(final ExplorerNodeService explorerNodeService,
                                 final Deque<DocRef> importParentPath,
                                 final DocRef nodeFileDocRef,
                                 final boolean useImportFolders,
                                 final boolean useImportNames)
            throws IOException {

        this.nodeFileDocRef = nodeFileDocRef;
        this.importParentDocRef = getParentDocRef(importParentPath);

        // Look for an existing ExplorerNode
        final Optional<ExplorerNode> optExistingNode =
                findExistingNode(explorerNodeService, this.importParentDocRef, this.nodeFileDocRef);
        LOGGER.info("Looking at {}/{}; already-exists={}", importParentDocRef.getName(), nodeFileDocRef.getName(), optExistingNode.isPresent());

        // If we did find an ExplorerNode then use the DocRef of that node instead of the
        // one from the node file, as it may be different as V1 import created new UUIDs on
        // import of folders.
        if (optExistingNode.isPresent()) {
            this.existingNode = optExistingNode.get();
            this.existingDocRef = this.existingNode.getDocRef();
            final Optional<ExplorerNode> optParentNode = explorerNodeService.getParent(existingNode.getDocRef());
            final DocRef existingParentDocRef;
            if (optParentNode.isPresent()) {
                this.existingParentNode = optParentNode.get();
                existingParentDocRef = this.existingParentNode.getDocRef();
            } else {
                throw new IOException("Parent node of the existing node '"
                                      + existingNode.getName() + "' cannot be found");
            }

            // Do we need to replace the UUID of the DocRef?
            if (!this.nodeFileDocRef.getUuid().equals(existingDocRef.getUuid())) {
                this.replaceUuid = true;
            } else {
                this.replaceUuid = false;
            }

            // Do we need to rename the ExplorerNode and DocRef?
            if (useImportNames && !existingDocRef.getName().equals(this.nodeFileDocRef.getName())) {
                this.rename = true;
            } else {
                this.rename = false;
            }

            // Do we need to move the ExplorerNode?

            if (useImportFolders && !this.importParentDocRef.equals(existingParentDocRef)) {
                this.moving = true;
            } else {
                this.moving = false;
            }

        } else {
            this.existingNode = null;
            this.existingDocRef = null;
            this.existingParentNode = null;
            this.replaceUuid = false;
            this.rename = false;
            this.moving = false;
        }

        this.destParentNode = getDestParentNode(explorerNodeService,
                useImportFolders,
                importParentDocRef,
                existingParentNode);

        LOGGER.info("Import paths: {}/{}", importParentPath, nodeFileDocRef);

        // TODO Store the path as String
        this.pathAsString = "TODO";
    }

    /**
     * Returns the parent node. This might be the parent from the import
     * data structure or the parent of any existing node.
     * @param explorerNodeService How to talk to the DB
     * @param useImportFolders    User settings - use folders from import
     *                            rather than existing folders
     * @param importParentDocRef  Parent doc ref defined in the import.
     * @param existingParentNode  Existing node, if any
     * @return                    Node for the parent of the node we're trying to
     *                            insert.
     * @throws IOException        If the parent node doesn't exist.
     */
    private static ExplorerNode getDestParentNode(final ExplorerNodeService explorerNodeService,
                                                  final boolean useImportFolders,
                                                  final DocRef importParentDocRef,
                                                  final @Nullable ExplorerNode existingParentNode)
            throws IOException {

        final ExplorerNode destParentNode;
        if (existingParentNode == null || useImportFolders) {
            final Optional<ExplorerNode> optParentNode = explorerNodeService.getNode(importParentDocRef);
            if (optParentNode.isEmpty()) {
                throw new IOException("Cannot find node for import parent '"
                                      + importParentDocRef.getName() + "'");
            }
            destParentNode = optParentNode.get();
        } else {
            destParentNode = existingParentNode;
        }

        return destParentNode;
    }

    /**
     * Utility method to return the last element in the given docRefPath
     * and ensure that it is some kind of Folder.
     * @param docRefPath   The path to get the last element of
     * @return             The last element of the path
     * @throws IOException if the path is empty, or the element isn't some kind
     *                     of Folder.
     */
    private static DocRef getParentDocRef(final Deque<DocRef> docRefPath) throws IOException {

        // The parent DocRef - this should be some kind of Folder
        final DocRef parentDocRef;

        if (docRefPath.isEmpty()) {
            // Shouldn't happen - error somewhere in the code
            throw new IOException("No parent for this node");
        } else {
            parentDocRef = docRefPath.getLast();
        }

        if (!(parentDocRef.equals(ExplorerConstants.SYSTEM_DOC_REF)
              || ExplorerConstants.isFolder(parentDocRef))) {
            // Error somewhere in the import structure
            throw new IOException("Error: parent node '" + parentDocRef.getName()
                                  + "' is not a Folder; instead it is a '" + parentDocRef.getType() +"'");
        }

        return parentDocRef;
    }

    /**
     * Holds the code to try to match up any existing node. Tries to find a node matching
     * the UUID of the DocRef. If that fails, and the nodeFileDocRef.getType() is 'Folder'
     * then it tries to find a match based on the name of the folder. This is to support
     * import into systems that have content from V1 import, where the UUID of the
     * import is generated randomly on import so the folders won't match up.
     * @param explorerNodeService How we talk to the DB
     * @param importParentDocRef  Where the node we are interested in is located
     * @param nodeFileDocRef      The DocRef we are trying to create.
     * @return                    An Optional containing the ExplorerNode matching
     *                            the nodeFileDocRef. Note that the UUID will not
     *                            match if the previous import was a V1 import.
     */
    private static Optional<ExplorerNode> findExistingNode(final ExplorerNodeService explorerNodeService,
                                                           final DocRef importParentDocRef,
                                                           final DocRef nodeFileDocRef)
            throws IOException {

        // Lookup based on UUID
        Optional<ExplorerNode> optExistingNode = explorerNodeService.getNode(nodeFileDocRef);

        // If that didn't find anything and this is a folder then try to match based on name
        if (optExistingNode.isEmpty() && nodeFileDocRef.getType().equals(ExplorerConstants.FOLDER_TYPE)) {
            final List<ExplorerNode> children = explorerNodeService.getChildren(importParentDocRef);
            ExplorerNode weakDocRef = null;
            for (final ExplorerNode node : children) {
                if (node.getDocRef().getName().equals(nodeFileDocRef.getName())) {
                    weakDocRef = node;
                    break;
                }
            }

            optExistingNode = Optional.ofNullable(weakDocRef);
        }

        return optExistingNode;
    }

    /**
     * Utility to convert explorer node path to docRef path
     *//*
    private static Deque<DocRef> nodePathToDocRefPath(final List<ExplorerNode> nodePath) {
        final Deque<DocRef> docRefPath = new ArrayDeque<>(nodePath.size());
        for (final ExplorerNode node : nodePath) {
            docRefPath.add(node.getDocRef());
        }

        return docRefPath;
    }*/

    /**
     * Resolves the path and name to a String path separated with
     * / delimiters.
     * @param docRefPath The path to resolve against
     * @param destName The destination name
     * @return A path in the form /A/B/C/D/name
     *//*
    private static String resolveToString(final Deque<DocRef> docRefPath,
                                          final String destName) {

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
    }*/

    /**
     * @return true if the ExplorerNode already exists.
     */
    public boolean nodeAlreadyExists() {
        return existingNode != null;
    }

    /**
     * @return The node that was specified in the import structure node file.
     */
    public DocRef getNodeFileDocRef() {
        return nodeFileDocRef;
    }

    /**
     * Shortcut method to return the name of the DocRef specified in the
     * .node file.
     * @return The name of the node file doc ref.
     */
    public String getNodeFileDocRefName() {
        return nodeFileDocRef.getName();
    }

    /**
     * @return the ExplorerNode representing the parent DocRef.
     */
    public ExplorerNode getDestParentNode() {
        return destParentNode;
    }

    /**
     * @return The parent of the node we're trying to import, as specified by
     * the import structure.
     */
    public DocRef getImportParentDocRef() {
        return importParentDocRef;
    }

    /**
     * @return The node path, if the ExplorerNode already exists, or null otherwise.
     */
    public @Nullable ExplorerNode getExistingNode() {
        return existingNode;
    }

    /**
     * @return The existing DocRef, if it exists, or null otherwise.
     */
    public @Nullable DocRef getExistingDocRef() {
        return existingDocRef;
    }

    /**
     * Returns the path to give to ImportState.
     */
    public String getDestPathForImportState() {
        return pathAsString;
    }

    /**
     * Whether we need to replace the UUID we've got recorded for the current
     * DocRef with the existing UUID. This will happen when we import a V2 Folder
     * into something created with a V1 import. The Folder UUID was created
     * randomly in V1 import so won't match the UUID in the V2 import.
     * @return true if the UUID must be replaced, false if it is ok.
     */
    public boolean isReplaceUuid() {
        return replaceUuid;
    }

    /**
     * @return true if the existing object must be moved,
     * false if it doesn't already exist or is not moving.
     */
    public boolean isMoving() {
        return moving;
    }

    /**
     * @return true if the existing object must be renamed.
     * false if it doesn't already exist or does not need to be renamed.
     */
    public boolean isRenamed() {
        return rename;
    }

    /**
     * @return Returns true if the type specified in the node file is
     * 'Folder'. Other types of folder result in false returned.
     */
    public boolean isNodeFileExactlyFolderType() {
        return ExplorerConstants.FOLDER_TYPE.equals(nodeFileDocRef.getType());
    }

}
