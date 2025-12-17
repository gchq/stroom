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
import stroom.explorer.shared.ExplorerNode;

import org.jspecify.annotations.NullMarked;

import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * Class encapsulates the logic for calculating the ImportState
 * stuff for an OwnerDoc + Non-Explorer Doc (a Processor Filter).
 */
@NullMarked
public class OwnerDocStateV2 {

    /** Given to ImportState as the source path */
    private final String importStateSourcePath;

    /** Given to ImportState as the destination path */
    private final String importStateDestPath;

    /**
     * Constructor.
     * @param explorerNodeService Where to get the node info from
     * @param ownerDocRef The owner document, returned from the
     *                    action handler for the non-explorer doc ref.
     * @param importParentPath The import path, as specified by the data structures
     *                         on disk.
     * @param useImportFolders From ImportSettings - whether to use existing or
     *                         imported folder structure.
     * @param useImportNames   From ImportSettings - whether to use existing or
     *                         imported object names.
     */
    public OwnerDocStateV2(final ExplorerNodeService explorerNodeService,
                           final DocRef ownerDocRef,
                           final Deque<DocRef> importParentPath,
                           final boolean useImportFolders,
                           final boolean useImportNames) {

        // Generate the text we'll use for the returned names
        final String name = ownerDocRef.getType()
                            + " "
                            + ownerDocRef.getUuid();
        final String suffix = " - (" + name + ")";

        final String sourcePath = resolveToString(importParentPath);
        final String sourceName = ownerDocRef.getName();
        String destPath = sourcePath;
        String destName = sourceName;

        final Optional<ExplorerNode> optExistingNode = explorerNodeService.getNode(ownerDocRef);
        if (optExistingNode.isPresent()) {
            if (!useImportFolders) {
                final List<ExplorerNode> parentNodes = explorerNodeService.getPath(ownerDocRef);
                destPath = resolveToString(parentNodes);
            }
            if (!useImportNames) {
                destName = optExistingNode.get().getName();
            }
        }

        importStateSourcePath = sourcePath + "/" + sourceName + suffix;
        importStateDestPath = destPath + "/" + destName + suffix;
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
        for (final DocRef docRef : docRefPath) {
            buf.append('/');
            buf.append(docRef.getName());
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
     * @return Source path to give to ImportState.
     */
    public String getSourcePath() {
        return importStateSourcePath;
    }

    /**
     * @return Destination path to give to ImportState.
     */
    public String getDestPath() {
        return importStateDestPath;
    }

}
