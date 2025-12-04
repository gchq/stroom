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

package stroom.index.impl;

import stroom.index.shared.LuceneVersion;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class LuceneProviderFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LuceneProviderFactory.class);

    public final Map<LuceneVersion, LuceneProvider> luceneProviders;

    @Inject
    LuceneProviderFactory(final Set<LuceneProvider> providers) {
        luceneProviders = providers
                .stream()
                .collect(Collectors.toMap(LuceneProvider::getLuceneVersion, Function.identity()));
    }

    public LuceneProvider get(final LuceneVersion luceneVersion) {
        final LuceneProvider luceneProvider = luceneProviders.get(luceneVersion);
        if (luceneProvider == null) {
            LOGGER.error("No Lucene provider found for version " + luceneVersion);
            throw new RuntimeException("No Lucene provider found for version " + luceneVersion);
        }
        return luceneProvider;
    }
}
