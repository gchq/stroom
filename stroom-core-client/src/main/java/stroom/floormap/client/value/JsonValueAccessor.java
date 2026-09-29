/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.floormap.client.value;

import com.google.gwt.json.client.JSONArray;
import com.google.gwt.json.client.JSONNumber;
import com.google.gwt.json.client.JSONObject;
import com.google.gwt.json.client.JSONParser;
import com.google.gwt.json.client.JSONString;
import com.google.gwt.json.client.JSONValue;

/**
 * {@link ValueAccessor} implementation for JSON values.
 *
 * <p>Paths use the dot-prefixed convention from
 * {@link ValuePathAccessor} (e.g. {@code ".type"} → JSON key
 * {@code "type"}). Numeric arrays are stored as {@link JSONArray}
 * instances containing {@link JSONNumber} elements.</p>
 */
public final class JsonValueAccessor implements ValueAccessor {

    static final JsonValueAccessor INSTANCE = new JsonValueAccessor();

    private JsonValueAccessor() {
        // Singleton
    }

    /**
     * {@inheritDoc}
     *
     * <p>In addition to the cases the contract lists, this returns {@code null} for
     * input that parses perfectly well but is not a JSON <em>object</em> — the
     * literal {@code null}, an array such as {@code [1,2]}, or a bare scalar. A
     * value's paths address named fields, so anything without them is unusable
     * here, and rejecting it up front is better than handing back a
     * {@link ParsedValue} that fails on first access.</p>
     */
    @Override
    public ParsedValue parse(final String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            final JSONValue val = JSONParser.parseStrict(raw);
            final JSONObject obj = val != null ? val.isObject() : null;
            return obj != null ? new ParsedValue(obj) : null;
        } catch (final Exception e) {
            return null;
        }
    }

    @Override
    public ParsedValue createEmpty(final String rootName) {
        return new ParsedValue(new JSONObject());
    }

    /**
     * {@inheritDoc}
     *
     * <p>JSON is typed, so this also returns {@code null} when a value <em>is</em>
     * present at {@code path} but is not a string — a number, boolean, array or
     * object. Only {@link JSONValue#isString()} counts.</p>
     *
     * <p>Note this is deliberately <strong>not</strong> what the XML accessor does:
     * XML element text carries no type, so there a numeric {@code 5} is simply the
     * string {@code "5"}. The two answers differ for the same logical field, and
     * both are correct for their format. Use {@link #getNumber} when a number is
     * what you want, regardless of format.</p>
     */
    @Override
    public String getString(final ParsedValue value, final String path) {
        final JSONObject json = asJson(value);
        if (json == null || path == null) {
            return null;
        }
        final JSONValue val = json.get(toKey(path));
        if (val == null) {
            return null;
        }
        final JSONString str = val.isString();
        return str != null ? str.stringValue() : null;
    }

    @Override
    public void setString(final ParsedValue value, final String path,
                          final String textValue) {
        final JSONObject json = asJson(value);
        if (json == null || path == null) {
            return;
        }
        final String key = toKey(path);
        if (textValue != null) {
            json.put(key, new JSONString(textValue));
        } else if (json.containsKey(key)) {
            json.put(key, null);
        }
    }

    @Override
    public double[] getArray(final ParsedValue value, final String path) {
        final JSONObject json = asJson(value);
        if (json == null || path == null) {
            return null;
        }
        final JSONValue val = json.get(toKey(path));
        if (val == null) {
            return null;
        }
        final JSONArray arr = val.isArray();
        if (arr == null || arr.size() == 0) {
            return null;
        }
        final double[] result = new double[arr.size()];
        for (int i = 0; i < arr.size(); i++) {
            final JSONValue elem = arr.get(i);
            final JSONNumber num = elem != null ? elem.isNumber() : null;
            if (num == null) {
                // Malformed: the interface contract is null for the whole array rather
                // than a zero for the bad element. Substituting zero used to fabricate
                // plausible-looking data — a matrix of six unparseable values became six
                // zeroes, which is a valid-looking but degenerate transform that silently
                // collapsed the fact to the origin.
                return null;
            }
            result[i] = num.doubleValue();
        }
        return result;
    }

    @Override
    public boolean hasValue(final ParsedValue value, final String path) {
        final JSONObject json = asJson(value);
        if (json == null || path == null) {
            return false;
        }
        final JSONValue val = json.get(toKey(path));
        // An explicit JSON null is the format saying "no value", so treat it as absent.
        return val != null && val.isNull() == null;
    }

    @Override
    public void setArray(final ParsedValue value, final String path,
                         final double[] numbers) {
        final JSONObject json = asJson(value);
        if (json == null || path == null || numbers == null) {
            return;
        }
        final JSONArray arr = new JSONArray();
        for (int i = 0; i < numbers.length; i++) {
            arr.set(i, new JSONNumber(numbers[i]));
        }
        json.put(toKey(path), arr);
    }

    @Override
    public Double getNumber(final ParsedValue value, final String path) {
        final JSONObject json = asJson(value);
        if (json == null || path == null) {
            return null;
        }
        final JSONValue val = json.get(toKey(path));
        if (val == null) {
            return null;
        }
        final JSONNumber num = val.isNumber();
        if (num != null) {
            return num.doubleValue();
        }
        // Lenient fallback: tolerate a numeric string.
        final JSONString str = val.isString();
        if (str != null) {
            try {
                return Double.parseDouble(str.stringValue().trim());
            } catch (final NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    @Override
    public void setNumber(final ParsedValue value, final String path,
                          final Double number) {
        final JSONObject json = asJson(value);
        if (json == null || path == null) {
            return;
        }
        final String key = toKey(path);
        if (number != null) {
            json.put(key, new JSONNumber(number));
        } else if (json.containsKey(key)) {
            json.put(key, null);
        }
    }

    @Override
    public String serialize(final ParsedValue value) {
        final JSONObject json = asJson(value);
        return json != null ? json.toString() : null;
    }

    @Override
    public boolean canParse(final String raw) {
        return raw != null && raw.trim().startsWith("{");
    }

    /**
     * Extracts the {@link JSONObject} from a {@link ParsedValue}.
     */
    private static JSONObject asJson(final ParsedValue value) {
        if (value == null) {
            return null;
        }
        final Object backing = value.getBacking();
        return backing instanceof JSONObject ? (JSONObject) backing : null;
    }

    /**
     * Strips the leading dot from a path to get the JSON key.
     */
    private static String toKey(final String path) {
        if (path != null && path.startsWith(".")) {
            return path.substring(1);
        }
        return path;
    }
}
