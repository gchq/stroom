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
import stroom.docref.DocRef;
import stroom.util.shared.ResultPage;

import java.util.Collection;

public interface DictionaryWordDao {

    void addWords(DocRef docRef, Collection<String> fields);

    ResultPage<String> findWords(FindWordCriteria criteria);

    int getWordCount(DocRef docRef);

    void addWord(AddWord addWord);

    void deleteWord(DeleteWord deleteWord);

    void deleteAll(DocRef docRef);

    void copyAll(DocRef source, DocRef dest);
}
