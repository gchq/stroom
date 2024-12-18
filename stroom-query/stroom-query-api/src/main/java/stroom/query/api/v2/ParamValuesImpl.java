package stroom.query.api.v2;

import java.util.Map;

public class ParamValuesImpl implements ParamValues {

    private final Map<String, String> map;

    public ParamValuesImpl(final Map<String, String> map) {
        this.map = map;
    }

    @Override
    public String get(final String key) {
        return map.get(key);
    }
}
