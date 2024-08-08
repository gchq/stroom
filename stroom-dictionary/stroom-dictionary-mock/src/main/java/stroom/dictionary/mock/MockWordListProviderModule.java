/*
 * Copyright 2018 Crown Copyright
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

package stroom.dictionary.mock;

import stroom.dictionary.api.WordListProvider;
import stroom.docref.DocRef;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MockWordListProviderModule extends AbstractModule {

    @Provides
    WordListProvider wordListProvider() {
        return new WordListProvider() {

            @Override
            public Set<DocRef> listDocuments() {
                return Collections.emptySet();
            }

            @Override
            public List<DocRef> findByNames(final List<String> names,
                                            final boolean allowWildCards,
                                            final boolean isCaseSensitive) {
                return Collections.emptyList();
            }

            @Override
            public String getCombinedData(final DocRef dictionaryRef) {
                return null;
            }

            @Override
            public String[] getWords(final DocRef dictionaryRef) {
                return null;
            }
        };
    }
}
