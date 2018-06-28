/*
 * Copyright 2016 Crown Copyright
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

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

public class XPathFilter implements Serializable {
    private static final long serialVersionUID = -5259490683649248946L;
    private String xPath;
    private MatchType matchType;
    private String value;
    private Boolean ignoreCase;
    private Map<String, Record> uniqueValues;

    public String getXPath() {
        return xPath;
    }

    public void setXPath(String xPath) {
        this.xPath = xPath;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(MatchType matchType) {
        this.matchType = matchType;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public Boolean isIgnoreCase() {
        return ignoreCase;
    }

    public void setIgnoreCase(Boolean ignoreCase) {
        this.ignoreCase = ignoreCase;
    }

    public Record getUniqueRecord(final String value) {
        if (uniqueValues == null) {
            return null;
        }

        return uniqueValues.get(value);
    }

    public void addUniqueValue(final String value, final Record record) {
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

    public enum MatchType implements HasDisplayValue {
        EXISTS("exists", false), CONTAINS("contains", true), EQUALS("equals", true), UNIQUE("unique values", false);

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
}
