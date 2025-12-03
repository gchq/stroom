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

package stroom.searchable.impl;

import stroom.docref.DocRef;
import stroom.explorer.api.IsSpecialExplorerDataSource;
import stroom.searchable.api.Searchable;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.Objects;

@Singleton
class SpecialExplorerDataSourceImpl implements IsSpecialExplorerDataSource {

    // Class name => searchable impl
    private final Map<String, Provider<Searchable>> searchableProviders;

    @Inject
    SpecialExplorerDataSourceImpl(final Map<String, Provider<Searchable>> searchableProviders) {
        this.searchableProviders = searchableProviders;
    }

    @Override
    public List<DocRef> getDataSourceDocRefs() {
        return searchableProviders.entrySet()
                .stream()
                .map(entry -> {
                    final Searchable searchable = entry.getValue().get();
                    Objects.requireNonNull(searchable, () ->
                            "Provider returned null for Searchable class " + entry.getKey());
                    return searchable;
                })
                .map(Searchable::getDataSourceDocRefs)
                .flatMap(NullSafe::stream)
                .filter(Objects::nonNull)
                .toList();
    }
}
