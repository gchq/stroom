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

import stroom.dictionary.shared.AddWord;
import stroom.dictionary.shared.DeleteWord;
import stroom.dictionary.shared.FindWordCriteria;
import stroom.dictionary.shared.SetWords;
import stroom.dictionary.shared.Words;
import stroom.docref.DocRef;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class DictionaryWordServiceImpl implements DictionaryWordService {

    private final DictionaryWordDao dictionaryWordDao;
    private final SecurityContext securityContext;

    @Inject
    public DictionaryWordServiceImpl(final DictionaryWordDao dictionaryWordDao,
                                     final SecurityContext securityContext) {
        this.dictionaryWordDao = dictionaryWordDao;
        this.securityContext = securityContext;
    }

    @Override
    public void addWords(final DocRef docRef, final Collection<String> fields) {
        dictionaryWordDao.addWords(docRef, fields);
    }

    @Override
    public ResultPage<String> findWords(final FindWordCriteria criteria) {
        final DocRef docRef = criteria.getDataSourceRef();

        // Check for read permission.
        if (docRef == null || !checkViewPermission(docRef)) {
            // If there is no read permission then return no fields.
            return ResultPage.createCriterialBasedList(Collections.emptyList(), criteria);
        }
        return dictionaryWordDao.findWords(criteria);
    }

    @Override
    public Boolean addWord(final AddWord addWord) {
        if (checkEditPermission(addWord.getDictionaryRef())) {
            dictionaryWordDao.addWord(addWord);
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    @Override
    public Boolean deleteWord(final DeleteWord deleteWord) {
        if (checkEditPermission(deleteWord.getDictionaryRef())) {
            dictionaryWordDao.deleteWord(deleteWord);
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    @Override
    public Boolean setWords(final SetWords setWords) {
        if (checkEditPermission(setWords.getDictionaryRef())) {
            final ResultPage<String> resultPage = dictionaryWordDao
                    .findWords(new FindWordCriteria(PageRequest.unlimited(), null, setWords.getDictionaryRef()));
            final Set<String> existingWords = new HashSet<>(resultPage.getValues());
            final Set<String> newWords = Arrays.stream(setWords.getWords().split("\n"))
                    .collect(Collectors.toSet());

            // Add new words.
            for (final String word : newWords) {
                if (!existingWords.contains(word)) {
                    dictionaryWordDao.addWord(new AddWord(setWords.getDictionaryRef(), word));
                }
            }

            // Remove old words.
            for (final String word : existingWords) {
                if (!newWords.contains(word)) {
                    dictionaryWordDao.deleteWord(new DeleteWord(setWords.getDictionaryRef(), word));
                }
            }

            return Boolean.TRUE;
        }
        return false;
    }

    @Override
    public Words getWords(final DocRef docRef) {
        if (checkViewPermission(docRef)) {
            final ResultPage<String> resultPage = dictionaryWordDao
                    .findWords(new FindWordCriteria(PageRequest.unlimited(), null, docRef));
            return new Words(String.join("\n", resultPage.getValues()));
        }
        return null;
    }

    private boolean checkEditPermission(final DocRef docRef) {
        return docRef != null && securityContext.hasDocumentPermission(docRef, DocumentPermission.EDIT);
    }

    private boolean checkViewPermission(final DocRef docRef) {
        return docRef != null && securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW);
    }

    @Override
    public int getWordCount(final DocRef docRef) {
        return dictionaryWordDao.getWordCount(docRef);
    }

    @Override
    public void deleteAll(final DocRef docRef) {
        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.DELETE)) {
            dictionaryWordDao.deleteAll(docRef);
        }
    }

    @Override
    public void copyAll(final DocRef source, final DocRef dest) {
        if (checkViewPermission(source)) {
            dictionaryWordDao.copyAll(source, dest);
        }
    }
}
