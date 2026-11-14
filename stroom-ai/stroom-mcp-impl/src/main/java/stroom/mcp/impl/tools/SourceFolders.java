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
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.shared.FeedDoc;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/// The folder of a source's own that promotion puts a build's documents in: the source feed's folder, unless that
/// folder is a parent of sources (the siblings' convention: subfolders named per source, each holding its feeds),
/// where the build's documents go in a folder named for the feed in it, its role subfolders (Events, Indexing ...)
/// below, never shared role folders directly under a parent holding other sources.
///
/// Seen in chat 177: the Azure audit feed sat directly in `/Feeds`, its siblings (Netskope, Delinea SecretServer)
/// each in a folder of their own; the registry and modules went to `/Feeds/Azure-Audit-V1.0-EVENTS`, but the
/// group's output feed and the source notes to `/Feeds` itself and the pipelines, XSLTs and index to `/Feeds/Events`
/// and `/Feeds/Indexing`, role folders shared by every source.
final class SourceFolders {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SourceFolders.class);

    private final WriteGuard guard;
    private final DocumentAccess documentAccess;
    // Whether each folder (normalised) holds other sources, as found: one instance a promotion call.
    private final Map<String, Boolean> parents = new HashMap<>();

    /// @param guard          Resolves folders by path.
    /// @param documentAccess Lists a folder's contents, as the user.
    SourceFolders(final WriteGuard guard, final DocumentAccess documentAccess) {
        this.guard = guard;
        this.documentAccess = documentAccess;
    }

    /// @param folder A folder, as an explorer path.
    /// @param source The source feed's name.
    /// @return The folder of the source's own: `folder/<source>` where the folder holds other sources, else the
    /// folder itself; normalised (`System/...`).
    String ownFolder(final String folder, final String source) {
        final String normalised = normalised(folder);
        if (source == null || source.isBlank() || lastPart(normalised).equalsIgnoreCase(source)
            || !holdsOtherSources(normalised, source)) {
            return normalised;
        }
        return normalised + "/" + source;
    }

    /// @param folder A folder, as an explorer path.
    /// @param source The source feed's name, whose own folder isn't another source's.
    /// @return True if the folder is a parent of sources: a subfolder of it (not a role subfolder, a version's
    /// folder, or the source's own) holds a feed.
    boolean holdsOtherSources(final String folder, final String source) {
        final String normalised = normalised(folder);
        return parents.computeIfAbsent(normalised.toLowerCase(Locale.ROOT) + "\n" + NullSafe.string(source)
                        .toLowerCase(Locale.ROOT),
                ignored -> findOtherSources(normalised, source));
    }

    /// @param path An explorer path.
    /// @return It as `System/...`, its folders' names stripped.
    static String normalised(final String path) {
        return String.join("/", WriteGuard.folderParts(path));
    }

    private boolean findOtherSources(final String folder, final String source) {
        try {
            final WriteGuard.ResolvedFolder resolved = guard.resolveFolder(folder);
            if (!resolved.missing().isEmpty()) {
                // A folder still to make holds nothing.
                return false;
            }
            for (final ExplorerNode child : documentAccess.children(resolved.folder().docRef())) {
                final DocRef ref = child.getDocRef();
                if (ref == null || !ExplorerConstants.FOLDER_TYPE.equals(ref.getType())
                    || ours(ref.getName(), source)) {
                    continue;
                }
                final boolean holdsAFeed = documentAccess.children(ref).stream()
                        .map(ExplorerNode::getDocRef)
                        .filter(Objects::nonNull)
                        .anyMatch(grandchild -> FeedDoc.TYPE.equals(grandchild.getType()));
                if (holdsAFeed) {
                    return true;
                }
            }
            return false;
        } catch (final RuntimeException e) {
            // Only the convention goes undetected: the folder as given.
            LOGGER.debug(() -> LogUtil.message("Couldn't list folder {}: {}", folder, e.getMessage()));
            return false;
        }
    }

    // A subfolder of the source's own making: a role subfolder, a version's folder, or the source's own folder.
    private static boolean ours(final String name, final String source) {
        final String said = NullSafe.string(name);
        return WriteGuard.ROLES.contains(said)
               || PromotionLayout.versions(said).equals(List.of(said))
               || said.equalsIgnoreCase(NullSafe.string(source));
    }

    private static String lastPart(final String path) {
        return path.substring(path.lastIndexOf('/') + 1);
    }
}
