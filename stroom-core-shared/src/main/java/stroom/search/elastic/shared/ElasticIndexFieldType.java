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

import stroom.datasource.api.v2.DataSourceField.DataSourceFieldType;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.util.shared.HasDisplayValue;

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
    ID(DataSourceFieldType.ID_FIELD, "Id", true, null),
    BOOLEAN(DataSourceFieldType.BOOLEAN_FIELD, "Boolean", false, new String[]{ "boolean" }),
    INTEGER(DataSourceFieldType.INTEGER_FIELD, "Integer", true, new String[]{ "integer", "short", "byte" }),
    LONG(DataSourceFieldType.LONG_FIELD, "Long", true, new String[]{ "long", "unsigned_long" }),
    FLOAT(DataSourceFieldType.FLOAT_FIELD, "Float", true, new String[]{ "float", "half_float", "scaled_float" }),
    DOUBLE(DataSourceFieldType.DOUBLE_FIELD, "Double", true, new String[]{ "double" }),
    DATE(DataSourceFieldType.DATE_FIELD, "Date", false, new String[]{ "date" }),
    TEXT(DataSourceFieldType.TEXT_FIELD, "Text", false, new String[]{ "text", "keyword", "constant_keyword", "wildcard" });

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

    private final DataSourceFieldType dataSourceFieldType;
    private final String displayValue;
    private final boolean numeric;
    private final Set<String> nativeTypes;
    private final List<Condition> supportedConditions;

    ElasticIndexFieldType(
            final DataSourceFieldType dataSourceFieldType,
            final String displayValue,
            final boolean numeric,
            final String[] nativeTypes
    ) {
        this.dataSourceFieldType = dataSourceFieldType;
        this.displayValue = displayValue;
        this.numeric = numeric;
        this.nativeTypes = nativeTypes != null ? new HashSet<>(Arrays.asList(nativeTypes)) : null;

        this.supportedConditions = getConditions();
    }

    public DataSourceFieldType getDataSourceFieldType() {
        return dataSourceFieldType;
    }

    public boolean isNumeric() {
        return numeric;
    }

    public Set<String> getNativeTypes() {
        return nativeTypes;
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

        if (dataSourceFieldType.equals(DataSourceFieldType.ID_FIELD)) {
            conditions.add(Condition.EQUALS);
            conditions.add(Condition.IN);
            conditions.add(Condition.IN_DICTIONARY);
        }
        else if (dataSourceFieldType.equals(DataSourceFieldType.DATE_FIELD) || dataSourceFieldType.isNumeric()) {
            conditions.add(Condition.EQUALS);
            conditions.add(Condition.GREATER_THAN);
            conditions.add(Condition.GREATER_THAN_OR_EQUAL_TO);
            conditions.add(Condition.LESS_THAN);
            conditions.add(Condition.LESS_THAN_OR_EQUAL_TO);
            conditions.add(Condition.BETWEEN);
            conditions.add(Condition.IN);
            conditions.add(Condition.IN_DICTIONARY);
        }
        else {
            conditions.add(Condition.EQUALS);
            conditions.add(Condition.CONTAINS);
            conditions.add(Condition.IN);
            conditions.add(Condition.IN_DICTIONARY);
        }

        return conditions;
    }

    /**
     * Given a native Elasticsearch data type, return an equivalent Stroom field type
     */
    public static ElasticIndexFieldType fromNativeType(String fieldName, String nativeType) {
        if (fieldName.equals(ElasticIndexConstants.EVENT_ID) || fieldName.equals(ElasticIndexConstants.STREAM_ID) || fieldName.equals(ElasticIndexConstants.FEED_ID)) {
            return ID;
        }

        if (nativeTypeRegistry.containsKey(nativeType))
            return nativeTypeRegistry.get(nativeType);

        throw new IllegalArgumentException("Unsupported field mapping type: '" + nativeType + "'");
    }
}
