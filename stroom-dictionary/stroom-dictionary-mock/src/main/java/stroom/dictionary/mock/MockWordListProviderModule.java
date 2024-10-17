/*
 * Copyright 2024 Crown Copyright
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
import stroom.dictionary.shared.WordList;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefDecorator;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
            public Optional<DocRef> findByUuid(final String uuid) {
                return Optional.empty();
            }

            @Override
            public List<DocRef> findByNames(final List<String> names,
                                            final boolean allowWildCards,
                                            final boolean isCaseSensitive) {
                return Collections.emptyList();
            }

            //            @Override
//            public List<DocRef> findByNames(final List<String> names,
//                                            final boolean allowWildCards,
//                                            final boolean isCaseSensitive) {
//                return Collections.emptyList();
//            }

            @Override
            public String getCombinedData(final DocRef dictionaryRef) {
                return null;
            }

            @Override
            public String[] getWords(final DocRef dictionaryRef) {
                return null;
            }

            @Override
            public WordList getCombinedWordList(final DocRef dictionaryRef,
                                                final DocRefDecorator docRefDecorator) {
                return null;
            }
        };
    }
}
