package stroom.search.elastic.shared;

import java.util.HashMap;
import java.util.Map;

public class ElasticNativeTypes {

    private static final Map<String, ElasticIndexFieldType> NATIVE_TYPE_MAP = new HashMap<>();

    static {
        NATIVE_TYPE_MAP.put("boolean", ElasticIndexFieldType.BOOLEAN);

        NATIVE_TYPE_MAP.put("integer", ElasticIndexFieldType.INTEGER);
        NATIVE_TYPE_MAP.put("short", ElasticIndexFieldType.INTEGER);
        NATIVE_TYPE_MAP.put("byte", ElasticIndexFieldType.INTEGER);
        NATIVE_TYPE_MAP.put("version", ElasticIndexFieldType.INTEGER);

        NATIVE_TYPE_MAP.put("unsigned_long", ElasticIndexFieldType.LONG);

        NATIVE_TYPE_MAP.put("float", ElasticIndexFieldType.FLOAT);
        NATIVE_TYPE_MAP.put("half_float", ElasticIndexFieldType.FLOAT);
        NATIVE_TYPE_MAP.put("scaled_float", ElasticIndexFieldType.FLOAT);

        NATIVE_TYPE_MAP.put("double", ElasticIndexFieldType.DOUBLE);

        NATIVE_TYPE_MAP.put("date", ElasticIndexFieldType.DATE);

        NATIVE_TYPE_MAP.put("text", ElasticIndexFieldType.TEXT);

        NATIVE_TYPE_MAP.put("keyword", ElasticIndexFieldType.KEYWORD);
        NATIVE_TYPE_MAP.put("constant_keyword", ElasticIndexFieldType.KEYWORD);
        NATIVE_TYPE_MAP.put("wildcard", ElasticIndexFieldType.KEYWORD);

        NATIVE_TYPE_MAP.put("ip", ElasticIndexFieldType.IPV4_ADDRESS);
    }

    /**
     * Given a native Elasticsearch data type, return an equivalent Stroom field type
     */
    public static ElasticIndexFieldType fromNativeType(final String fieldName, final String nativeType) {
        if (NATIVE_TYPE_MAP.containsKey(nativeType)) {
            return NATIVE_TYPE_MAP.get(nativeType);
        }

        throw new IllegalArgumentException("Field '" + fieldName + "' has an unsupported mapping type '" +
                nativeType + "'");
    }
}
