/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.planb.impl.fs;

import stroom.planb.shared.HasSharedFileStore;
import stroom.planb.shared.PlanBDocument;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.util.shared.NullSafe;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Resolves the shared file store a document's data lives on.
 *
 * <p>A store is either kept on a shared filesystem or transferred between nodes over HTTP, never
 * both, and which one it is follows from its settings: only a store whose settings are
 * {@link HasSharedFileStore} has a shared file store.
 *
 * <p>Code handed a document it cannot vouch for asks {@link #isConfigured} first. Code that has
 * already established the document has one asks for the location with {@link #rootOf}, which throws
 * rather than guessing where to write.
 */
public final class SharedFileStore {

    private SharedFileStore() {
    }

    /**
     * The shared file store the document's data lives on, empty when it has none. A document can also
     * have one that has not been filled in yet, which {@link #isConfigured} rules out.
     */
    public static Optional<SharedFileStoreSettings> settingsOf(final PlanBDocument doc) {
        return doc != null && doc.getSettings() instanceof final HasSharedFileStore s
                ? Optional.ofNullable(s.getSharedFileStore())
                : Optional.empty();
    }

    /**
     * Where the document keeps its data on the shared file store, empty when it has no shared file
     * store or has not been given a path yet.
     */
    public static Optional<String> sharedPathOf(final PlanBDocument doc) {
        return settingsOf(doc)
                .map(SharedFileStoreSettings::getSharedPath)
                .filter(NullSafe::isNonBlankString);
    }

    /**
     * Whether the document keeps its data on a shared file store that is ready to be used — it has
     * one, and it names both a path and at least one shard.
     */
    public static boolean isConfigured(final PlanBDocument doc) {
        return sharedPathOf(doc).isPresent() && shardCountOf(doc) > 0;
    }

    /**
     * The root of the document's shared file store.
     *
     * <p>Throws where the document has none. Callers are the shared file store's own machinery, which
     * runs only for documents that keep their data there, so an absent one is a wiring mistake rather
     * than a case to handle — and failing here says so, where returning a default would write the
     * store's data somewhere unintended.
     */
    public static Path rootOf(final PlanBDocument doc) {
        return sharedPathOf(doc)
                .map(Path::of)
                .orElseThrow(() -> new IllegalStateException(
                        "'" + (doc == null ? "null" : doc.getName()) + "' has no shared file store"));
    }

    /**
     * How many ways the document's data is split across its shared file store, or 0 where it has
     * none.
     */
    public static int shardCountOf(final PlanBDocument doc) {
        return settingsOf(doc).map(SharedFileStoreSettings::getShardCount).orElse(0);
    }
}
