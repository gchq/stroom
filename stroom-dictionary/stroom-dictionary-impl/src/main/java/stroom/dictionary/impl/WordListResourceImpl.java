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

package stroom.dictionary.impl;

import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.WordList;
import stroom.dictionary.shared.WordListResource;
import stroom.docref.DocRef;
import stroom.docrefinfo.api.DocRefDecorator;
import stroom.event.logging.rs.api.AutoLogged;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@AutoLogged
class WordListResourceImpl implements WordListResource {

    private static final Logger LOGGER = LoggerFactory.getLogger(WordListResourceImpl.class);

    private final Provider<WordListProvider> wordListProviderProvider;
    private final Provider<DocRefDecorator> docRefDecoratorProvider;

    @Inject
    WordListResourceImpl(final Provider<WordListProvider> wordListProviderProvider,
                         final Provider<DocRefDecorator> docRefDecoratorProvider) {
        this.wordListProviderProvider = wordListProviderProvider;
        this.docRefDecoratorProvider = docRefDecoratorProvider;
    }

    @Override
    public WordList getWords(final String uuid) {
        try {
            final DocRef dictDocRef = DictionaryDoc.buildDocRef()
                    .uuid(uuid)
                    .build();

            @SuppressWarnings("UnnecessaryLocalVariable") final WordList wordList = wordListProviderProvider.get()
                    .getCombinedWordList(dictDocRef, docRefDecoratorProvider.get());
            return wordList;
        } catch (final Exception e) {
            LOGGER.error(e.getMessage(), e);
            return WordList.EMPTY;
        }
    }
}
