/*
 * Copyright 2026 Crown Copyright
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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// Runs the [AbstractValueAccessorContractTest] against the JSON-shaped double
/// used by the parser and editor-model suites.
class TestMapValueAccessorContract extends AbstractValueAccessorContractTest {

    @Override
    protected ValueAccessor accessor() {
        return MapValueAccessor.INSTANCE;
    }

    @Override
    protected String docWithNumericArray(final String field, final double... values) {
        final StringBuilder sb = new StringBuilder("{\"").append(field).append("\":[");
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            sb.append(values[i]);
        }
        return sb.append("]}").toString();
    }

    @Override
    protected String docWithMalformedNumericArray(final String field) {
        return "{\"" + field + "\":[1,\"nope\",3]}";
    }

    @Override
    protected String docWithEmptyNumericArray(final String field) {
        return "{\"" + field + "\":[]}";
    }

    @Override
    protected String docWithString(final String field, final String value) {
        return "{\"" + field + "\":\"" + value + "\"}";
    }

    @Override
    protected String emptyDoc() {
        return "{}";
    }

    @Override
    protected String path(final String field) {
        return "." + field;
    }

    @Override
    protected String unparseableRaw() {
        return "not-json-at-all";
    }
    // -----------------------------------------------------------------------
    // JSON-specific behaviour this double must match in production
    //
    // These cannot live in the shared contract because they are properties of
    // JSON's type system, and XML has none — element text is just text. But they
    // must still be pinned somewhere, because the divergences the review found here
    // were between this double and the *production JSON* accessor, not between
    // formats. Leaving them unasserted is what let the two drift.
    // -----------------------------------------------------------------------

    /// JSON is typed, so only a string is a string.
    ///
    /// Production `JsonValueAccessor.getString` asks `JSONValue.isString()`
    /// and returns `null` for anything else. This double used to call
    /// `toString()` on whatever it found, so a numeric `5` read back as
    /// `"5.0"` — a value the browser never produces, asserted by every test that
    /// used this double.
    @Test
    void testGetString_nonStringScalarYieldsNull() {
        final ValueAccessor a = accessor();
        assertThat(a.getString(a.parse("{\"n\":5}"), ".n"))
                .as("a JSON number is not a string")
                .isNull();
        assertThat(a.getString(a.parse("{\"b\":true}"), ".b"))
                .as("a JSON boolean is not a string")
                .isNull();
        assertThat(a.getString(a.parse("{\"arr\":[1,2]}"), ".arr"))
                .as("a JSON array is not a string")
                .isNull();
        assertThat(a.getString(a.parse("{\"s\":\"text\"}"), ".s"))
                .isEqualTo("text");
    }

    /// An explicit JSON `null` is absent, not a value.
    @Test
    void testHasValue_explicitJsonNullIsAbsent() {
        final ValueAccessor a = accessor();
        final ParsedValue value = a.parse("{\"maybe\":null}");
        assertThat(value).isNotNull();
        assertThat(a.hasValue(value, ".maybe")).isFalse();
        assertThat(a.getString(value, ".maybe")).isNull();
    }

    /// Only an object is a parseable document.
    ///
    /// Production keeps the parse result only if `isObject()` succeeds. This
    /// double used to hand back a [ParsedValue] wrapping a null map for the
    /// literal `null`, which looked like a successful parse and then failed on
    /// first use instead of being rejected up front.
    @Test
    void testParse_nonObjectYieldsNull() {
        final ValueAccessor a = accessor();
        assertThat(a.parse("null")).as("the literal null is not a document").isNull();
        assertThat(a.parse("[1,2]")).as("an array is not a document").isNull();
        assertThat(a.parse("5")).as("a number is not a document").isNull();
    }

}
