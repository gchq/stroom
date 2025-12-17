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

package stroom.data.shared;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class DataInfoSection {

    @JsonProperty
    private final String title;
    @JsonProperty
    private final List<Entry> entries;

    @JsonCreator
    public DataInfoSection(@JsonProperty("title") final String title,
                           @JsonProperty("entries") final List<Entry> entries) {
        this.title = title;
        this.entries = entries;
    }

    public String getTitle() {
        return title;
    }

    public List<Entry> getEntries() {
        return entries;
    }


    // --------------------------------------------------------------------------------


    @JsonInclude(Include.NON_NULL)
    public static class Entry {

        @JsonProperty
        private final String key;
        @JsonProperty
        private final String value;

        @JsonCreator
        public Entry(@JsonProperty("key") final String key,
                     @JsonProperty("value") final String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public String getValue() {
            return value;
        }
    }
}
