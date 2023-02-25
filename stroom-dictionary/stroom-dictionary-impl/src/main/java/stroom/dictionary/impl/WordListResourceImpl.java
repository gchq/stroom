/*
 * Copyright 2017 Crown Copyright
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

package stroom.dictionary.impl;

import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.WordListResource;
import stroom.docref.DocRef;
import stroom.event.logging.rs.api.AutoLogged;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged
class WordListResourceImpl implements WordListResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(WordListResourceImpl.class);

    private final Provider<WordListProvider> wordListProviderProvider;

    @Inject
    WordListResourceImpl(final Provider<WordListProvider> wordListProviderProvider) {
        this.wordListProviderProvider = wordListProviderProvider;
    }

    @Override
    public List<String> getWords(final String uuid) {
        List<String> list = Collections.emptyList();
        try {
            final String[] arr = wordListProviderProvider
                    .get()
                    .getWords(new DocRef(DictionaryDoc.DOCUMENT_TYPE, uuid));
            if (arr != null) {
                list = List.of(arr);
            }
        } catch (final RuntimeException e) {
            LOGGER.error(e.getMessage(), e);
        }
        return list;
    }
}
