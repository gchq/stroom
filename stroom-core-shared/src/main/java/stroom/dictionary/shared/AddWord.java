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

package stroom.dictionary.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class AddWord {

    @JsonProperty
    private final DocRef dictionaryRef;
    @JsonProperty
    private final String word;

    @JsonCreator
    public AddWord(@JsonProperty("dictionaryRef") final DocRef dictionaryRef,
                   @JsonProperty("word") final String word) {
        this.dictionaryRef = dictionaryRef;
        this.word = word;
    }

    public DocRef getDictionaryRef() {
        return dictionaryRef;
    }

    public String getWord() {
        return word;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AddWord that = (AddWord) o;
        return Objects.equals(dictionaryRef, that.dictionaryRef) &&
               Objects.equals(word, that.word);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dictionaryRef, word);
    }

    @Override
    public String toString() {
        return "AddWord{" +
               "dictionaryRef=" + dictionaryRef +
               ", word=" + word +
               '}';
    }
}
