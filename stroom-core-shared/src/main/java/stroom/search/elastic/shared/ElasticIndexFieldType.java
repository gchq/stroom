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

import stroom.datasource.api.v2.AbstractField;
import stroom.datasource.api.v2.BooleanField;
import stroom.datasource.api.v2.DateField;
import stroom.datasource.api.v2.DoubleField;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.FloatField;
import stroom.datasource.api.v2.IdField;
import stroom.datasource.api.v2.IntegerField;
import stroom.datasource.api.v2.IpV4AddressField;
import stroom.datasource.api.v2.KeywordField;
import stroom.datasource.api.v2.LongField;
import stroom.datasource.api.v2.TextField;
import stroom.docref.HasDisplayValue;
import stroom.query.api.v2.ExpressionTerm.Condition;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @see "https://www.elastic.co/guide/en/elasticsearch/reference/current/mapping-types.html"
 */
public enum ElasticIndexFieldType implements HasDisplayValue {
    ID(FieldType.ID, "Id", true, null),
    BOOLEAN(FieldType.BOOLEAN, "Boolean", false, new String[]{"boolean"}),
    INTEGER(FieldType.INTEGER, "Integer", true,
            new String[]{"integer", "short", "byte", "version"}),
    LONG(FieldType.LONG, "Long", true, new String[]{"long", "unsigned_long"}),
    FLOAT(FieldType.FLOAT, "Float", false, new String[]{"float", "half_float", "scaled_float"}),
    DOUBLE(FieldType.DOUBLE, "Double", false, new String[]{"double"}),
    DATE(FieldType.DATE, "Date", false, new String[]{"date"}),
    TEXT(FieldType.TEXT, "Text", false, new String[]{"text"}),
    KEYWORD(FieldType.KEYWORD, "Keyword", false,
            new String[]{"keyword", "constant_keyword", "wildcard"}),
    IPV4_ADDRESS(FieldType.IPV4_ADDRESS, "IpV4Address", false, new String[]{"ip"});

    private static Map<String, ElasticIndexFieldType> nativeTypeRegistry = new HashMap<>();

    static {
        final Map<String, ElasticIndexFieldType> nativeTypeRegistry = new HashMap<>();
        for (ElasticIndexFieldType fieldType : values()) {
            // Register this field type's native types for easy retrieval
            if (fieldType.nativeTypes != null) {
                fieldType.nativeTypes.forEach(nativeType -> nativeTypeRegistry.putIfAbsent(nativeType, fieldType));
            }
        }

        ElasticIndexFieldType.nativeTypeRegistry = nativeTypeRegistry;
    }

    private final FieldType dataSourceFieldType;
    private final String displayValue;
    private final boolean numeric;
    private final Set<String> nativeTypes;
    private final List<Condition> supportedConditions;

    ElasticIndexFieldType(final FieldType dataSourceFieldType,
                          final String displayValue,
                          final boolean numeric,
                          final String[] nativeTypes) {
        this.dataSourceFieldType = dataSourceFieldType;
        this.displayValue = displayValue;
        this.numeric = numeric;
        this.nativeTypes = nativeTypes != null
                ? new HashSet<>(Arrays.asList(nativeTypes))
                : null;
        this.supportedConditions = getConditions();
    }

    public boolean isNumeric() {
        return numeric;
    }

    public List<Condition> getSupportedConditions() {
        return supportedConditions;
    }

    @Override
    public String getDisplayValue() {
        return displayValue;
    }

    /**
     * Determine the query expression conditions that apply to this field type
     */
    private List<Condition> getConditions() {
        final List<Condition> conditions = new ArrayList<>();

        if (FieldType.DATE.equals(dataSourceFieldType) ||
                FieldType.IPV4_ADDRESS.equals(dataSourceFieldType) ||
                numeric) {
            conditions.add(Condition.EQUALS);
            conditions.add(Condition.GREATER_THAN);
            conditions.add(Condition.GREATER_THAN_OR_EQUAL_TO);
            conditions.add(Condition.LESS_THAN);
            conditions.add(Condition.LESS_THAN_OR_EQUAL_TO);
            conditions.add(Condition.BETWEEN);
            conditions.add(Condition.IN);
            conditions.add(Condition.IN_DICTIONARY);
        } else {
            conditions.add(Condition.EQUALS);
            conditions.add(Condition.IN);
            conditions.add(Condition.IN_DICTIONARY);
            conditions.add(Condition.MATCHES_REGEX);
        }

        return conditions;
    }

    /**
     * Given a native Elasticsearch data type, return an equivalent Stroom field type
     */
    public static ElasticIndexFieldType fromNativeType(final String fieldName, final String nativeType) {
        if (nativeTypeRegistry.containsKey(nativeType)) {
            return nativeTypeRegistry.get(nativeType);
        }

        throw new IllegalArgumentException("Field '" + fieldName + "' has an unsupported mapping type '" +
                nativeType + "'");
    }

    /**
     * Returns an `AbstractField` instance, based on the field's data type
     */
    public AbstractField toDataSourceField(final String fieldName, final Boolean isIndexed)
            throws IllegalArgumentException {
        switch (dataSourceFieldType) {
            case ID:
                return new IdField(fieldName, isIndexed, supportedConditions);
            case BOOLEAN:
                return new BooleanField(fieldName, isIndexed, supportedConditions);
            case INTEGER:
                return new IntegerField(fieldName, isIndexed, supportedConditions);
            case LONG:
                return new LongField(fieldName, isIndexed, supportedConditions);
            case FLOAT:
                return new FloatField(fieldName, isIndexed, supportedConditions);
            case DOUBLE:
                return new DoubleField(fieldName, isIndexed, supportedConditions);
            case DATE:
                return new DateField(fieldName, isIndexed, supportedConditions);
            case TEXT:
                return new TextField(fieldName, isIndexed, supportedConditions);
            case KEYWORD:
                return new KeywordField(fieldName, isIndexed, supportedConditions);
            case IPV4_ADDRESS:
                return new IpV4AddressField(fieldName, isIndexed, supportedConditions);
            default:
                return null;
        }
    }
}
