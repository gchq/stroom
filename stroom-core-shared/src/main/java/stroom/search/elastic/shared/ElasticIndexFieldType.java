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

package stroom.search.elastic.shared;

import stroom.datasource.api.v2.BooleanField;
import stroom.datasource.api.v2.ConditionSet;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.DoubleField;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.FloatField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.IntegerField;
import stroom.datasource.api.v2.IpV4AddressField;
import stroom.datasource.api.v2.KeywordField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.QueryField;
import stroom.datasource.api.v2.TextField;
import stroom.docref.HasDisplayValue;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @see "https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-types.html"
 */
public enum ElasticIndexFieldType implements HasDisplayValue {
    ID(FieldType.ID, "Id", true),
    BOOLEAN(FieldType.BOOLEAN, "Boolean", false),
    INTEGER(FieldType.INTEGER, "Integer", true),
    LONG(FieldType.LONG, "Long", true),
    FLOAT(FieldType.FLOAT, "Float", false),
    DOUBLE(FieldType.DOUBLE, "Double", false),
    DATE(FieldType.DATE, "Date", false),
    TEXT(FieldType.TEXT, "Text", false),
    KEYWORD(FieldType.KEYWORD, "Keyword", false),
    IPV4_ADDRESS(FieldType.IPV4_ADDRESS, "IpV4Address", false);

    private final FieldType dataSourceFieldType;
    private final String displayValue;
    private final boolean numeric;
    private final ConditionSet supportedConditions;

    ElasticIndexFieldType(final FieldType dataSourceFieldType,
                          final String displayValue,
                          final boolean numeric) {
        this.dataSourceFieldType = dataSourceFieldType;
        this.displayValue = displayValue;
        this.numeric = numeric;
        this.supportedConditions = getConditions();
    }

    public boolean isNumeric() {
        return numeric;
    }

    public ConditionSet getSupportedConditions() {
        return supportedConditions;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    /**
     * Determine the query expression conditions that apply to this field type
     */
    private ConditionSet getConditions() {
        if (FieldType.DATE.equals(dataSourceFieldType) ||
                FieldType.IPV4_ADDRESS.equals(dataSourceFieldType) ||
                numeric) {
            return ConditionSet.ELASTIC_NUMERIC;
        }
        return ConditionSet.ELASTIC_TEXT;
    }

    /**
     * Returns an `AbstractField` instance, based on the field's data type
     */
    public QueryField toDataSourceField(final String fieldName, final Boolean isIndexed)
            throws IllegalArgumentException {
        switch (dataSourceFieldType) {
            case ID:
                return new IdField(fieldName, supportedConditions, null, isIndexed);
            case BOOLEAN:
                return new BooleanField(fieldName, supportedConditions, null, isIndexed);
            case INTEGER:
                return new IntegerField(fieldName, supportedConditions, null, isIndexed);
            case LONG:
                return new LongField(fieldName, supportedConditions, null, isIndexed);
            case FLOAT:
                return new FloatField(fieldName, supportedConditions, null, isIndexed);
            case DOUBLE:
                return new DoubleField(fieldName, supportedConditions, null, isIndexed);
            case DATE:
                return new DateField(fieldName, supportedConditions, null, isIndexed);
            case TEXT:
                return new TextField(fieldName, supportedConditions, null, isIndexed);
            case KEYWORD:
                return new KeywordField(fieldName, supportedConditions, null, isIndexed);
            case IPV4_ADDRESS:
                return new IpV4AddressField(fieldName, supportedConditions, null, isIndexed);
            default:
                return null;
        }
    }
}
