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

package stroom.ai.client;

import stroom.ai.shared.DocumentReference;
import stroom.ai.shared.DocumentReferenceContext;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;

import java.util.ArrayList;
import java.util.List;

/// Which of the items selected in the Explorer tree can be attached to a chat ("Attach to chat"): documents and
/// folders the user can view, but not the System or Favourites roots.
public final class AiDocumentReferences {

    private AiDocumentReferences() {
        // Static methods only.
    }

    /// @param viewableNodes The selected items the user can view, System documents already left out.
    /// @return References to those that can be attached, in the same order. Empty if none can.
    public static List<DocumentReference> references(final List<ExplorerNode> viewableNodes) {
        final List<DocumentReference> references = new ArrayList<>();
        if (viewableNodes == null) {
            return references;
        }
        for (final ExplorerNode node : viewableNodes) {
            if (node != null && !ExplorerConstants.isRootNode(node)) {
                // The server works out where each is in the tree, as the tree here may not show it.
                references.add(new DocumentReference(node.getDocRef(), null));
            }
        }
        return references;
    }

    /// @param viewableNodes The selected items the user can view, System documents already left out.
    /// @return What to attach to the chat, or null if none of them can be.
    public static DocumentReferenceContext context(final List<ExplorerNode> viewableNodes) {
        final List<DocumentReference> references = references(viewableNodes);
        return references.isEmpty()
                ? null
                : new DocumentReferenceContext(references);
    }
}
