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

package stroom.pipeline.shared;

import stroom.docref.HasDisplayValue;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;

@JsonInclude(Include.NON_NULL)
public class XPathFilter {

    public static final MatchType DEFAULT_MATCH_TYPE = MatchType.EXISTS;

    @JsonProperty
    private String path;
    @JsonProperty
    private MatchType matchType;
    @JsonProperty
    private SearchType searchType;
    @JsonProperty
    private String value;
    @JsonProperty
    private Boolean ignoreCase;
    @JsonProperty
    private Map<String, Rec> uniqueValues;

    public XPathFilter() {
    }

    @JsonCreator
    public XPathFilter(@JsonProperty("path") final String path,
                       @JsonProperty("matchType") final MatchType matchType,
                       @JsonProperty("value") final String value,
                       @JsonProperty("ignoreCase") final Boolean ignoreCase,
                       @JsonProperty("uniqueValues") final Map<String, Rec> uniqueValues,
                       @JsonProperty("searchType") final SearchType searchType) {
        this.path = path;
        this.matchType = matchType;
        this.value = value;
        this.ignoreCase = ignoreCase;
        this.uniqueValues = uniqueValues;
        this.searchType = searchType;
    }

    public String getPath() {
        return path;
    }

    public void setPath(final String path) {
        this.path = path;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(final MatchType matchType) {
        this.matchType = matchType;
    }

    public SearchType getSearchType() {
        return searchType;
    }

    public void setSearchType(final SearchType searchType) {
        this.searchType = searchType;
    }

    public String getValue() {
        return value;
    }

    public void setValue(final String value) {
        this.value = value;
    }

    public Boolean isIgnoreCase() {
        return ignoreCase;
    }

    public void setIgnoreCase(final Boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    public Map<String, Rec> getUniqueValues() {
        return uniqueValues;
    }

    public Rec getUniqueRecord(final String value) {
        if (uniqueValues == null) {
            return null;
        }

        return uniqueValues.get(value);
    }

    public void addUniqueValue(final String value, final Rec record) {
        if (uniqueValues == null) {
            uniqueValues = new HashMap<>();
        }

        uniqueValues.put(value, record);
    }

    public void clearUniqueValues() {
        if (uniqueValues != null) {
            uniqueValues.clear();
        }
    }

    @Override
    public String toString() {
        return "XPathFilter{" +
               "path='" + path + '\'' +
               ", matchType=" + matchType +
               ", value='" + value + '\'' +
               ", ignoreCase=" + ignoreCase +
               ", uniqueValues=" + uniqueValues +
               '}';
    }


    // --------------------------------------------------------------------------------


    public enum MatchType implements HasDisplayValue {
        EXISTS("Exists", false),
        CONTAINS("Contains", true),
        EQUALS("Equals", true),
        NOT_EQUALS("Not equals", true),
        NOT_CONTAINS("Does not contain", true),
        NOT_EXISTS("Does not exist", false),
        UNIQUE("Unique values", false);

        private final String displayValue;
        private final boolean needsValue;

        MatchType(final String displayValue, final boolean needsValue) {
            this.displayValue = displayValue;
            this.needsValue = needsValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }

        public boolean isNeedsValue() {
            return needsValue;
        }
    }

    public enum SearchType implements HasDisplayValue {
        ALL("All text"),
        XPATH("XPath");

        private final String displayValue;

        SearchType(final String displayValue) {
            this.displayValue = displayValue;
        }

        @Override
        public String getDisplayValue() {
            return displayValue;
        }
    }
}
