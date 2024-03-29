package stroom.search.elastic.shared;

import stroom.datasource.api.v2.FieldType;

import java.util.HashMap;
import java.util.Map;

public class ElasticNativeTypes {

    private static final Map<String, FieldType> NATIVE_TYPE_MAP = new HashMap<>();

    static {
        NATIVE_TYPE_MAP.put("boolean", FieldType.BOOLEAN);

        NATIVE_TYPE_MAP.put("integer", FieldType.INTEGER);
        NATIVE_TYPE_MAP.put("short", FieldType.INTEGER);
        NATIVE_TYPE_MAP.put("byte", FieldType.INTEGER);
        NATIVE_TYPE_MAP.put("version", FieldType.INTEGER);

        NATIVE_TYPE_MAP.put("unsigned_long", FieldType.LONG);

        NATIVE_TYPE_MAP.put("float", FieldType.FLOAT);
        NATIVE_TYPE_MAP.put("half_float", FieldType.FLOAT);
        NATIVE_TYPE_MAP.put("scaled_float", FieldType.FLOAT);

        NATIVE_TYPE_MAP.put("double", FieldType.DOUBLE);

        NATIVE_TYPE_MAP.put("date", FieldType.DATE);

        NATIVE_TYPE_MAP.put("text", FieldType.TEXT);

        NATIVE_TYPE_MAP.put("keyword", FieldType.KEYWORD);
        NATIVE_TYPE_MAP.put("constant_keyword", FieldType.KEYWORD);
        NATIVE_TYPE_MAP.put("wildcard", FieldType.KEYWORD);

        NATIVE_TYPE_MAP.put("ip", FieldType.IPV4_ADDRESS);
    }

    /**
     * Given a native Elasticsearch data type, return an equivalent Stroom field type
     */
    public static FieldType fromNativeType(final String fieldName, final String nativeType) {
        if (NATIVE_TYPE_MAP.containsKey(nativeType)) {
            return NATIVE_TYPE_MAP.get(nativeType);
        }

        throw new IllegalArgumentException("Field '" + fieldName + "' has an unsupported mapping type '" +
                nativeType + "'");
    }
}
