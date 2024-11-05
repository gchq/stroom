package stroom.dashboard.client.table;

import java.util.Map;

public class ComponentSelection {
    private Map<String, String> map;

    public ComponentSelection(final Map<String, String> map) {
        this.map = map;
    }

    public String get(final String key) {
        return map.get(key);
    }

    public Map<String, String> getMap() {
        return map;
    }
}
