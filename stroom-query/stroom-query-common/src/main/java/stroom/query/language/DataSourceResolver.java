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

package stroom.query.language;

import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.query.common.v2.DataSourceProviderRegistry;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class DataSourceResolver {

    private final Provider<DocFinder> docFinderProvider;
    private final Provider<DataSourceProviderRegistry> dataSourceProviderRegistryProvider;

    @Inject
    public DataSourceResolver(final Provider<DocFinder> docFinderProvider,
                              final Provider<DataSourceProviderRegistry> dataSourceProviderRegistryProvider) {
        this.docFinderProvider = docFinderProvider;
        this.dataSourceProviderRegistryProvider = dataSourceProviderRegistryProvider;
    }

    public DocRef resolveDataSourceRef(final String name) {
        final DataSourceProviderRegistry dataSourceProviderRegistry = dataSourceProviderRegistryProvider.get();

        // Try by uuid first.
        final Optional<DocRef> byUuid = dataSourceProviderRegistry.findDataSourceByUuid(name);
        if (byUuid.isPresent()) {
            return byUuid.get();
        }

        // Now try by name.
        final List<DocRef> docRefs = dataSourceProviderRegistry.findDataSourceByName(name);
        if (docRefs.isEmpty()) {
            throw new RuntimeException("Data source \"" +
                                       name +
                                       "\" not found. You may not have permission to use it.");
        } else if (docRefs.size() > 1) {
            throw new RuntimeException("Multiple data sources found for \"" + name + "\"");
        }
        return docRefs.getFirst();
    }

    public DocRef findVisualisationDoc(final String name) {
        return resolveDocRef("Visualisation", name);
    }

    public DocRef findDictionaryDoc(final String name) {
        return resolveDocRef("Dictionary", name);
    }

    private DocRef resolveDocRef(final String type, final String name) {
        final DocFinder docFinder = docFinderProvider.get();

        // Try by UUID.
        final DocRef docRef = new DocRef(type, name);
        final Optional<DocRef> optionalDocRef = docFinder.decorateIfExists(docRef);
        if (optionalDocRef.isPresent()) {
            return optionalDocRef.get();
        }

        // Try by name.
        final List<DocRef> docRefs = docFinder.findByName(type, name, false);
        if (docRefs.isEmpty()) {
            throw new RuntimeException(type + " \"" + name + "\" not found");
        } else if (docRefs.size() > 1) {
            throw new RuntimeException("Multiple " +
                                       type.toLowerCase(Locale.ROOT) +
                                       " items found with name \"" +
                                       name +
                                       "\"");
        }
        return docRefs.getFirst();
    }
}
