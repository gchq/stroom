/*
 * Copyright 2026 Crown Copyright
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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.DocumentAccess;
import stroom.docref.DocRef;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerNode;

import jakarta.inject.Inject;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/// Where documents are in the explorer, as the builds' tools need it (the Stroom MCP server's `tools/builds.py`
/// `_folder_of`, `_path` and `_documentation_beside`), read as the user: only what they can view is found. A
/// folder is given by its path from System, e.g. `System/Feeds/Acme`, as promotion's destinations are written.
class DocumentPlaces {

    private final DocumentAccess documentAccess;
    private final PipelineIndex pipelineIndex;

    /// @param documentAccess Reads the explorer tree, checking View.
    /// @param pipelineIndex  Finds documents by name.
    @Inject
    DocumentPlaces(final DocumentAccess documentAccess, final PipelineIndex pipelineIndex) {
        this.documentAccess = documentAccess;
        this.pipelineIndex = pipelineIndex;
    }

    /// @param ref A document.
    /// @return The folder it is in, from System (e.g. `System/Feeds/Acme`); empty for one the user can't view, or
    /// that isn't in the tree.
    Optional<String> folderOf(final DocRef ref) {
        if (ref == null || !documentAccess.canView(ref) || documentAccess.node(ref).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(PipelineIndex.path(documentAccess, ref));
    }

    /// @param uuid A document's UUID.
    /// @return It, with its type and name, if the user can view it.
    Optional<DocRef> find(final String uuid) {
        return documentAccess.findDocRef(uuid);
    }

    /// `_documentation_beside`: the Documentation doc of a name in a document's own folder.
    ///
    /// @param doc  A document, e.g. a pipeline or an index doc.
    /// @param name The Documentation doc's name, e.g. the pipeline's.
    /// @return It, if the user can view it.
    Optional<DocRef> documentationBeside(final DocRef doc, final String name) {
        if (folderOf(doc).isEmpty()) {
            return Optional.empty();
        }
        return documentAccess.parent(doc)
                .map(documentAccess::children)
                .orElse(List.of())
                .stream()
                .map(ExplorerNode::getDocRef)
                .filter(Objects::nonNull)
                .filter(child -> DocumentationDoc.TYPE.equals(child.getType()) && name.equals(child.getName()))
                .findFirst();
    }

    /// @param type A document type.
    /// @param name A name, exactly.
    /// @return The documents of the type and name the user can view.
    List<DocRef> named(final String type, final String name) {
        return pipelineIndex.named(type, name, 20).stream()
                .filter(ref -> name.equals(ref.getName()))
                .toList();
    }
}
