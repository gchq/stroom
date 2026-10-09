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

package stroom.sqlstore.impl;

import stroom.sqlstore.api.UpdatableTemporalStore;
import stroom.sqlstore.shared.UnknownStoreException;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

@Singleton
public class UpdatableTemporalStoreProvider {

    private final Provider<UpdatableSqlTemporalStore> sqlTemporalStoreProvider;

    @Inject
    public UpdatableTemporalStoreProvider(final Provider<UpdatableSqlTemporalStore> sqlTemporalStoreProvider) {
        this.sqlTemporalStoreProvider = sqlTemporalStoreProvider;
    }

    /**
     * Returns the store implementation that serves the named map.
     *
     * <p>This no longer verifies that a document of that name exists. It used to list every
     * {@code SqlTemporalStoreDoc} in the system on every call - which was both an unfiltered
     * listing that ignored the caller's permissions, and, because callers invoke this once per
     * reference entry, a full document-store scan per row on the ingest path. Resolution now
     * happens once inside {@link UpdatableSqlTemporalStore}, which matches the name against
     * only the documents the caller may see and refuses an ambiguous match.</p>
     *
     * @param mapName the map name the caller intends to operate on; used only for the
     *                unknown-store message, never for scoping
     * @return the store; never {@code null}
     */
    public UpdatableTemporalStore get(final String mapName) {
        if (mapName == null || mapName.isBlank()) {
            throw new UnknownStoreException("No store name supplied.");
        }
        // TODO: Future: Check for PlanBDoc here too.
        return sqlTemporalStoreProvider.get();
    }
}
