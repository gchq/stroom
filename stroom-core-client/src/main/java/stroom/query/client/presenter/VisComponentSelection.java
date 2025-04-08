package stroom.query.client.presenter;

import stroom.dashboard.client.table.ComponentSelection;
import stroom.query.api.Param;

import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONValue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

public class VisComponentSelection implements ComponentSelection {

    private final Map<String, String> values;

    private VisComponentSelection(final Map<String, String> values) {
        this.values = values;
    }

    public static List<ComponentSelection> create(final JSONValue selection) {
        final List<ComponentSelection> list = new ArrayList<>();
        final JSONArray array = selection.isArray();
        if (array != null) {
            for (int i = 0; i < array.size(); i++) {
                list.add(VisComponentSelection.createFromObject(array.get(i).isObject()));
            }
        } else {
            list.add(VisComponentSelection.createFromObject(selection.isObject()));
        }
        return list;
    }

    private static ComponentSelection createFromObject(final JSONObject obj) {
        final Map<String, String> map = new HashMap<>();
        if (obj != null) {
            for (final String key : obj.keySet()) {
                final JSONValue v = obj.get(key);
                if (v.isString() != null) {
                    map.put(key, v.isString().stringValue());
                } else {
                    map.put(key, v.toString());
                }
            }
        }
        return new VisComponentSelection(map);
    }

    @Override
    public List<Param> getParams() {
        final List<Param> params = new ArrayList<>();
        for (final Entry<String, String> entry : values.entrySet()) {
            params.add(new Param(entry.getKey(), entry.getValue()));
        }
        return params;
    }

    @Override
    public String getParamValue(final String key) {
        return values.get(key);
    }
}
