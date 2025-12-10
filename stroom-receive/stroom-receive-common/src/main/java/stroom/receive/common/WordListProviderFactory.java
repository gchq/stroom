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

package stroom.receive.common;

import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.WordList;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefDecorator;
import stroom.docstore.api.DocumentNotFoundException;
import stroom.receive.rules.shared.HashedReceiveDataRules;
import stroom.util.shared.NullSafe;
import stroom.util.string.StringUtil;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class WordListProviderFactory {

    private static final String[] EMPTY_STRING_ARR = new String[0];

    public WordListProvider create(final Map<String, DictionaryDoc> uuidToDictMap) {
        if (NullSafe.isEmptyMap(uuidToDictMap)) {
            return NotFoundWordListProvider.INSTANCE;
        } else {
            return new WordListProvider() {

                @Override
                public String getCombinedData(final DocRef dictionaryRef) {
                    if (dictionaryRef != null) {
                        // All these dicts have been flattened, so we don't need to resolve any imports
                        final DictionaryDoc dictionaryDoc = uuidToDictMap.get(dictionaryRef.getUuid());
                        if (dictionaryDoc == null) {
                            // We shouldn't get here as if we have an inDict term, we should have the dict
                            // for it.
                            throw new DocumentNotFoundException(dictionaryRef);
                        } else {
                            if (NullSafe.hasItems(dictionaryDoc.getImports())) {
                                throw new IllegalArgumentException("Only flattened dictionaries are supported");
                            }
                            return dictionaryDoc.getData();
                        }
                    } else {
                        return "";
                    }
                }

                @Override
                public String[] getWords(final DocRef dictionaryRef) {
                    final String data = getCombinedData(dictionaryRef);
                    if (NullSafe.isBlankString(data)) {
                        return EMPTY_STRING_ARR;
                    } else {
                        return StringUtil.splitToLines(data, true)
                                .toArray(String[]::new);
                    }
                }

                @Override
                public WordList getCombinedWordList(final DocRef dictionaryRef,
                                                    final DocRefDecorator docRefDecorator) {
                    throw new UnsupportedOperationException("Not supported as this is only needed for the UI");
                }

                @Override
                public List<DocRef> findByName(final String name) {
                    return uuidToDictMap.values()
                            .stream()
                            .filter(Objects::nonNull)
                            .filter(doc -> Objects.equals(name, doc.getName()))
                            .map(DictionaryDoc::asDocRef)
                            .toList();
                }

                @Override
                public Optional<DocRef> findByUuid(final String uuid) {
                    return Optional.ofNullable(uuidToDictMap.get(uuid))
                            .map(DictionaryDoc::asDocRef);
                }
            };
        }
    }


    // --------------------------------------------------------------------------------


    /**
     * For use when there are no dictionaries in the {@link HashedReceiveDataRules} so
     * none of these methods should be called in practice.
     */
    private static class NotFoundWordListProvider implements WordListProvider {

        private static final NotFoundWordListProvider INSTANCE = new NotFoundWordListProvider();

        @Override
        public String getCombinedData(final DocRef dictionaryRef) {
            throw new DocumentNotFoundException(dictionaryRef);
        }

        @Override
        public String[] getWords(final DocRef dictionaryRef) {
            throw new DocumentNotFoundException(dictionaryRef);
        }

        @Override
        public WordList getCombinedWordList(final DocRef dictionaryRef,
                                            final DocRefDecorator docRefDecorator) {
            throw new DocumentNotFoundException(dictionaryRef);
        }

        @Override
        public List<DocRef> findByName(final String name) {
            return Collections.emptyList();
        }

        @Override
        public Optional<DocRef> findByUuid(final String uuid) {
            return Optional.empty();
        }
    }
}
