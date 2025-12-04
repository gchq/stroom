/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Class to represent the state of the Node File
 * and the associated DocRef.
 * Whether it already exists, and if it does, whether
 * it needs to change its name or move.
 * Used in the V2 Import/Export code.
 */
@NullMarked
class ImportDocRefStateV2 {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(ImportDocRefStateV2.class);

    /** DocRef defined by contents of .node file in the import structure on disk */
    private final DocRef importDocRef;

    /** DocRef that represents the parent of the importDocRef in the import structure on disk */
    private final DocRef importParentDocRef;

    /** DocRef that represents the existing item, or null if there isn't one */
    private @Nullable final DocRef existingDocRef;

    /** The existing node, if it exists */
    private @Nullable final ExplorerNode existingNode;

    /** The node that is the parent of the existing item in Stroom */
    private @Nullable final ExplorerNode existingParentNode;

    /** The destination parent node; either import or existing */
    private @Nullable final ExplorerNode destParentNode;

    /** Whether this DocRef needs renaming */
    private final boolean rename;

    /** Whether this DocRef needs moving */
    private final boolean moving;

    /** Path to return for ImportState records */
    private final String destPathForImportStateAsString;

    /**
     * Constructor.
     * @param explorerNodeService Where to get the node data from.
     * @param importParentPath    The stack holding the ancestor docRefs for
     *                            this node file.
     * @param importDocRef      The docRef that we want node state for, from the .node file.
     * @param useImportFolders    From ImportSettings. Use the folders specified in the
     *                            import data structure rather than any existing folders.
     * @param useImportNames      From ImportSettings. Use the item name specified in the
     *                            import data strcture rather than any existing name.
     */
    public ImportDocRefStateV2(final ExplorerNodeService explorerNodeService,
                               final Deque<DocRef> importParentPath,
                               final DocRef importDocRef,
                               final boolean useImportFolders,
                               final boolean useImportNames)
            throws IOException {

        this.importDocRef = importDocRef;
        this.importParentDocRef = getParentDocRef(importParentPath);

        // For telling users where things end up
        String destPath = resolveToString(importParentPath);
        String destName = importDocRef.getName();

        // Look for an existing ExplorerNode
        final Optional<ExplorerNode> optExistingNode =
                findExistingNode(explorerNodeService, this.importParentDocRef, this.importDocRef);
        LOGGER.debug("Looking at {}/{}; already-exists={}",
                importParentDocRef.getName(),
                importDocRef.getName(),
                optExistingNode.isPresent());

        // If we did find an ExplorerNode then use the DocRef of that node instead of the
        // one from the node file, as it may be different as V1 import created new UUIDs on
        // import of folders.
        if (optExistingNode.isPresent()) {
            this.existingNode = optExistingNode.get();
            this.existingDocRef = this.existingNode.getDocRef();
            final Optional<ExplorerNode> optParentNode = explorerNodeService.getParent(existingNode.getDocRef());
            final DocRef existingParentDocRef;
            if (optParentNode.isPresent()) {
                existingParentNode = optParentNode.get();
                existingParentDocRef = this.existingParentNode.getDocRef();
                LOGGER.debug("Found existing parent node named '{}'", existingParentNode.getName());
            } else {
                throw new IOException("Parent node of the existing node '"
                                      + existingNode.getName() + "' cannot be found");
            }

            // Do we need to rename the ExplorerNode and DocRef?
            if (useImportNames && !existingDocRef.getName().equals(this.importDocRef.getName())) {
                this.rename = true;
                destName = existingDocRef.getName();
                LOGGER.debug("Need to rename the explorerNode from '{}' to '{}'",
                        existingDocRef.getName(),
                        importDocRef.getName());
            } else {
                this.rename = false;
            }

            // Do we need to move the ExplorerNode?
            if (useImportFolders && !this.importParentDocRef.equals(existingParentDocRef)) {
                this.moving = true;

                final Optional<ExplorerNode> optImportParentNode = explorerNodeService.getNode(importParentDocRef);
                if (optImportParentNode.isEmpty()) {
                    throw new IOException("Cannot find node for import parent '"
                                          + importParentDocRef.getName() + "'");
                }
                destParentNode = optImportParentNode.get();
                destPath = resolveToString(explorerNodeService.getPath(existingParentNode.getDocRef()));
                LOGGER.debug("Moving from '{}' to '{}'",
                        existingParentDocRef.getName(),
                        importParentDocRef.getName());
            } else {
                this.moving = false;
                this.destParentNode = null;
            }

        } else {
            this.existingNode = null;
            this.existingDocRef = null;
            this.existingParentNode = null;
            this.destParentNode = null;
            this.rename = false;
            this.moving = false;
        }

        // Record where this file is going to end up
        this.destPathForImportStateAsString = destPath + '/' + destName;

        LOGGER.debug("Import paths: {}/{}", importParentPath, importDocRef);
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
                                  + "' is not a Folder; instead it is a '" + parentDocRef.getType() + "'");
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
                                                           final DocRef nodeFileDocRef) {

        // Lookup based on UUID
        Optional<ExplorerNode> optExistingNode = explorerNodeService.getNode(nodeFileDocRef);

        // If that didn't find anything and this is a folder then try to match based on name
        if (optExistingNode.isEmpty() && nodeFileDocRef.getType().equals(ExplorerConstants.FOLDER_TYPE)) {
            final List<ExplorerNode> children = explorerNodeService.getChildren(importParentDocRef);
            ExplorerNode weakDocRef = null;
            for (final ExplorerNode node : children) {
                if (node.getDocRef().getName().equals(nodeFileDocRef.getName())) {
                    LOGGER.warn("Found Folder docRef based on name ('{}') rather than UUID ('{}' != '{}')",
                            nodeFileDocRef.getName(), nodeFileDocRef.getUuid(), node.getUuid());
                    weakDocRef = node;
                    break;
                }
            }

            optExistingNode = Optional.ofNullable(weakDocRef);
        }

        return optExistingNode;
    }

    /**
     * Utility function to convert a stack of DocRefs to a
     * String path.
     * @param docRefPath The path to convert
     * @return The string representing the path.
     */
    private static String resolveToString(final Deque<DocRef> docRefPath) {

        // Convert docRefPath to String
        final StringBuilder buf = new StringBuilder();
        for (final DocRef dr : docRefPath) {
            buf.append('/');
            buf.append(dr.getName());
        }

        return buf.toString();
    }

    /**
     * Utility function to convert a list of ExplorerNodes to a
     * String path.
     * @param nodePath The path to convert.
     * @return The string representing the path.
     */
    private static String resolveToString(final List<ExplorerNode> nodePath) {
        final StringBuilder buf = new StringBuilder();
        for (final ExplorerNode node : nodePath) {
            buf.append('/');
            buf.append(node.getName());
        }

        return buf.toString();
    }

    /**
     * @return The docRef to use as the imported docRef when importing
     * a Folder into Stroom.
     */
    public DocRef getImportedFolderDocRef() {
        return Objects.requireNonNullElse(existingDocRef, importDocRef);
    }

    /**
     * @return true if the ExplorerNode already exists.
     */
    public boolean nodeAlreadyExists() {
        return existingNode != null;
    }

    /**
     * @return The node that was specified in the import structure node file.
     */
    public DocRef getImportDocRef() {
        return importDocRef;
    }

    /**
     * @return the ExplorerNode representing the parent DocRef.
     * Only valid if isMoving() returns true.
     */
    public @Nullable ExplorerNode getDestParentNode() {
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
     * Returns the path to give to ImportState so the user knows where
     * the file ended up.
     */
    public String getDestPathForImportState() {
        return destPathForImportStateAsString;
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
        return ExplorerConstants.FOLDER_TYPE.equals(importDocRef.getType());
    }
}
