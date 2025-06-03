package stroom.query.api;

import java.util.Map;

public class ParamValuesImpl implements ParamValues {

    private final Map<String, String> map;

    public ParamValuesImpl(final Map<String, String> map) {
        this.map = map;
    }

    @Override
    public String getParamValue(final String key) {
        return map.get(key);
    }
}
