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

package stroom.dashboard.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "key",
        "value",
        "values",
        "dictionary",
        "useDictionary"
})
@JsonInclude(Include.NON_NULL)
public class ListInputComponentSettings implements ComponentSettings {

    @JsonProperty
    private final String key;
    @JsonProperty
    private final String value;
    @JsonProperty
    private final List<String> values;
    @JsonProperty
    private final DocRef dictionary;
    @JsonProperty
    private final boolean useDictionary;

    @JsonCreator
    public ListInputComponentSettings(@JsonProperty("key") final String key,
                                      @JsonProperty("value") final String value,
                                      @JsonProperty("values") final List<String> values,
                                      @JsonProperty("dictionary") final DocRef dictionary,
                                      @JsonProperty("useDictionary") final boolean useDictionary) {
        this.key = key;
        this.value = value;
        this.values = values;
        this.dictionary = dictionary;
        this.useDictionary = useDictionary;
    }

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    public List<String> getValues() {
        return values;
    }

    public DocRef getDictionary() {
        return dictionary;
    }

    public boolean isUseDictionary() {
        return useDictionary;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ListInputComponentSettings that = (ListInputComponentSettings) o;
        return useDictionary == that.useDictionary && Objects.equals(key, that.key) && Objects.equals(
                value,
                that.value) && Objects.equals(values, that.values) && Objects.equals(dictionary,
                that.dictionary);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, value, values, dictionary, useDictionary);
    }

    @Override
    public String toString() {
        return "DropDownInputComponentSettings{" +
                "key='" + key + '\'' +
                ", value='" + value + '\'' +
                ", values=" + values +
                ", dictionary=" + dictionary +
                ", useDictionary=" + useDictionary +
                '}';
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private String key;
        private String value;
        private List<String> values;
        private DocRef dictionary;
        private boolean useDictionary;

        private Builder() {
        }

        private Builder(final ListInputComponentSettings keyValueInputComponentSettings) {
            this.key = keyValueInputComponentSettings.key;
            this.value = keyValueInputComponentSettings.value;
            this.values = keyValueInputComponentSettings.values;
            this.dictionary = keyValueInputComponentSettings.dictionary;
            this.useDictionary = keyValueInputComponentSettings.useDictionary;
        }

        public Builder key(final String key) {
            this.key = key;
            return this;
        }

        public Builder value(final String value) {
            this.value = value;
            return this;
        }

        public Builder values(final List<String> values) {
            this.values = values;
            return this;
        }

        public Builder dictionary(final DocRef dictionary) {
            this.dictionary = dictionary;
            return this;
        }

        public Builder useDictionary(final boolean useDictionary) {
            this.useDictionary = useDictionary;
            return this;
        }

        public ListInputComponentSettings build() {
            return new ListInputComponentSettings(
                    key,
                    value,
                    values,
                    dictionary,
                    useDictionary
            );
        }
    }
}
