package stroom.search.elastic.shared;

import stroom.query.api.datasource.FieldType;

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

        NATIVE_TYPE_MAP.put("long", FieldType.LONG);
        NATIVE_TYPE_MAP.put("unsigned_long", FieldType.LONG);

        NATIVE_TYPE_MAP.put("float", FieldType.FLOAT);
        NATIVE_TYPE_MAP.put("half_float", FieldType.FLOAT);
        NATIVE_TYPE_MAP.put("scaled_float", FieldType.FLOAT);

        NATIVE_TYPE_MAP.put("double", FieldType.DOUBLE);

        NATIVE_TYPE_MAP.put("date", FieldType.DATE);
        NATIVE_TYPE_MAP.put("date_nanos", FieldType.DATE);

        NATIVE_TYPE_MAP.put("text", FieldType.TEXT);

        NATIVE_TYPE_MAP.put("keyword", FieldType.KEYWORD);
        NATIVE_TYPE_MAP.put("constant_keyword", FieldType.KEYWORD);
        NATIVE_TYPE_MAP.put("wildcard", FieldType.KEYWORD);

        NATIVE_TYPE_MAP.put("ip", FieldType.IPV4_ADDRESS);

        NATIVE_TYPE_MAP.put("dense_vector", FieldType.DENSE_VECTOR);
    }

    /**
     * Given a native Elasticsearch data type, return an equivalent Stroom field type
     */
    public static FieldType fromNativeType(final String fieldName, final String nativeType)
            throws UnsupportedTypeException {
        final FieldType fieldType = NATIVE_TYPE_MAP.get(nativeType);

        if (fieldType == null) {
            throw new UnsupportedTypeException("Field '" + fieldName + "' has an unsupported mapping type '" +
                    nativeType + "'");
        }
        return fieldType;
    }
}
