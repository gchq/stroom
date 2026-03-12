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
public class SetWords {

    @JsonProperty
    private final DocRef dictionaryRef;
    @JsonProperty
    private final String words;

    @JsonCreator
    public SetWords(@JsonProperty("dictionaryRef") final DocRef dictionaryRef,
                    @JsonProperty("words") final String words) {
        this.dictionaryRef = dictionaryRef;
        this.words = words;
    }

    public DocRef getDictionaryRef() {
        return dictionaryRef;
    }

    public String getWords() {
        return words;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SetWords that = (SetWords) o;
        return Objects.equals(dictionaryRef, that.dictionaryRef) &&
               Objects.equals(words, that.words);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dictionaryRef, words);
    }

    @Override
    public String toString() {
        return "SetWords{" +
               "dictionaryRef=" + dictionaryRef +
               ", words=" + words +
               '}';
    }
}
