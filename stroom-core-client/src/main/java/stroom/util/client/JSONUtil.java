/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.util.client;

import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;

public class JSONUtil {

    private JSONUtil() {
        // Utility class.
    }

    public static JSONValue parse(final String json) {
        if (json != null && !json.isEmpty()) {
            return JSONParser.parseStrict(json);
        }

        return null;
    }

    public static JSONObject getObject(final JSONValue v) {
        if (v != null) {
            return v.isObject();
        }
        return null;
    }

    public static JSONArray getArray(final JSONValue v) {
        if (v != null) {
            return v.isArray();
        }
        return null;
    }

    public static String getString(final JSONValue v) {
        if (v != null) {
            final JSONString jsonString = v.isString();
            if (jsonString != null) {
                return jsonString.stringValue();
            }
        }
        return null;
    }

    public static Integer getInteger(final JSONValue v) {
        if (v != null) {
            final JSONNumber jsonNumber = v.isNumber();
            if (jsonNumber != null) {
                return Integer.valueOf((int) jsonNumber.doubleValue());
            }
        }
        return null;
    }

    public static Double getDouble(final JSONValue v) {
        if (v != null) {
            final JSONNumber jsonNumber = v.isNumber();
            if (jsonNumber != null) {
                return Double.valueOf(jsonNumber.doubleValue());
            }
        }
        return null;
    }

    public static String[] getStrings(final JSONValue v) {
        String[] strings = new String[0];
        final JSONArray array = getArray(v);
        if (array != null) {
            strings = new String[array.size()];
            for (int i = 0; i < array.size(); i++) {
                strings[i] = getString(array.get(i));
            }
        }

        return strings;
    }
}
