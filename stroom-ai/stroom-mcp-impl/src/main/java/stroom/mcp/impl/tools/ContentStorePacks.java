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

import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.docref.DocRef;
import stroom.gitrepo.shared.GitRepoDoc;
import stroom.util.shared.NullSafe;

import java.util.Optional;

/// Whether a document came from a content pack Stroom's Content Store installed: it sits under a Git repository
/// node (a [GitRepoDoc]) that holds the Content Store's metadata. Stroom pulls such a pack again on an upgrade,
/// overwriting its documents by UUID, so anything the tools wrote into one would be lost; its changes belong in the
/// pack's source. A Git repository the user syncs themselves (no Content Store metadata) is written as any folder:
/// Stroom pushes what changes in it.
///
/// A content pack imported as a zip leaves no mark on its documents in Stroom (ContentPackImport records nothing
/// per document), so it can't be told from documents made by hand.
final class ContentStorePacks {

    // A guard against a cycle in what the Explorer says: no real tree is this deep.
    private static final int MAX_DEPTH = 100;

    private final DocumentAccess documentAccess;

    /// @param documentAccess Reads documents and their folders, as the user.
    ContentStorePacks(final DocumentAccess documentAccess) {
        this.documentAccess = documentAccess;
    }

    /// @param ref A document.
    /// @return The content pack it came from, in words (the Git repository node's name and the pack's id); empty
    /// where it is under no Content Store pack, or its folders can't be read.
    Optional<String> packOf(final DocRef ref) {
        if (ref == null) {
            return Optional.empty();
        }
        Optional<DocRef> at = documentAccess.parent(ref);
        for (int depth = 0; at.isPresent() && depth < MAX_DEPTH; depth++) {
            final DocRef folder = at.get();
            if (GitRepoDoc.TYPE.equals(folder.getType())) {
                // The nearest repository is the one that holds it: a user's own repository is written as any folder.
                return documentAccess.read(folder, GitRepoDoc.class)
                        .filter(repo -> repo.getContentStoreMetadata() != null
                                        || NullSafe.isNonBlankString(repo.getContentStoreContentPackId()))
                        .map(repo -> "'" + NullSafe.nonBlankStringElse(repo.getName(), folder.getName()) + "'"
                                     + (NullSafe.isNonBlankString(repo.getContentStoreContentPackId())
                                ? " (content pack " + repo.getContentStoreContentPackId() + ")"
                                : ""));
            }
            at = documentAccess.parent(folder);
        }
        return Optional.empty();
    }
}
