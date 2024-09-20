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

package stroom.dictionary.api;

import stroom.dictionary.shared.WordList;
import stroom.docref.DocRef;

import java.util.List;
import java.util.Optional;

public interface WordListProvider {

    /**
     * @return The complete wordList as a single string with words delimited by {@code \n}.
     * The last item may not have a trailing {@code \n}.
     * A 'word' is a line in the dictionary.
     * Follows and combines words from dictionary imports.
     */
    String getCombinedData(DocRef dictionaryRef);

    /**
     * @return The complete word list as a simple array or words.
     * A 'word' is a line in the dictionary.
     * Follows and combines words from dictionary imports.
     */
    String[] getWords(DocRef dictionaryRef);

    WordList getCombinedWordList(DocRef dictionaryRef);

    List<DocRef> findByName(String name);

    Optional<DocRef> findByUuid(String uuid);
}
