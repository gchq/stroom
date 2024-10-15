package stroom.query.common.v2;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class RowValueMap {

    private static final RowValueMap EMPTY = new RowValueMap(Collections.emptyMap());

    private final Map<String, Object> map;

    public RowValueMap() {
        this(new HashMap<>());
    }

    private RowValueMap(final Map<String, Object> map) {
        this.map = map;
    }

    public Object get(final String key) {
        return map.get(key);
    }

    public void put(final String key, final Object value) {
        map.put(key, value);
    }

    public static RowValueMap empty() {
        return EMPTY;
    }
}
