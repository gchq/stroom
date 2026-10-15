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

package stroom.gwt.workbench.framework.client.args;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestArgsCodec {

    @Test
    void testEncode_storybookFormat() {
        // The same format React Storybook puts in its URLs
        assertThat(ArgsCodec.encode(Args.of("loading", false, "variant", "contained-primary")))
                .isEqualTo("loading:!false;variant:contained-primary");
        assertThat(ArgsCodec.encode(Args.of("count", 5))).isEqualTo("count:5");
        assertThat(ArgsCodec.encode(Args.of("count", 1e20))).isEqualTo("count:100000000000000000000");
        assertThat(ArgsCodec.encode(Args.of("tags", List.of("a", "b")))).isEqualTo("tags[0]:a;tags[1]:b");
        assertThat(ArgsCodec.encode(Args.of("text", "Click me"))).isEqualTo("text:Click+me");
        assertThat(ArgsCodec.encode(Args.empty().withNull("text"))).isEqualTo("text:!null");
        assertThat(ArgsCodec.encode(Args.empty())).isEmpty();
    }

    @Test
    void testEncode_specialValues() {
        assertThat(ArgsCodec.encode(Args.of("colour", "#ff4785"))).isEqualTo("colour:!hex(ff4785)");
        assertThat(ArgsCodec.encode(Args.of("colour", "#FFF"))).isEqualTo("colour:!hex(FFF)");
        assertThat(ArgsCodec.encode(Args.of("colour", "rgba(255, 0, 0, 0.5)")))
                .isEqualTo("colour:!rgba(255,0,0,0.5)");
        assertThat(ArgsCodec.encode(Args.of("colour", "hsl(120, 50%, 25%)")))
                .isEqualTo("colour:!hsl(120,50,25)");
        assertThat(ArgsCodec.encode(Args.of("when", "2026-10-05T12:30:00.000Z")))
                .isEqualTo("when:!date(2026-10-05T12:30:00.000Z)");
    }

    @Test
    void testEncode_leavesOutUnsafeArgs() {
        // As Storybook does
        assertThat(ArgsCodec.encode(Args.of("text", "a;b:c!d%e", "ok", "yes"))).isEqualTo("ok:yes");
        assertThat(ArgsCodec.encode(Args.of("text", "<script>alert(1)</script>"))).isEmpty();
        assertThat(ArgsCodec.encode(Args.of("a.b", "x", "<b>", "x"))).isEmpty();
        assertThat(ArgsCodec.encode(Args.of("tags", List.of("a", "b;c")))).isEmpty();
        assertThat(ArgsCodec.encode(Args.of("count", Double.NaN))).isEmpty();
        assertThat(ArgsCodec.encode(Args.of("count", Double.POSITIVE_INFINITY))).isEmpty();
    }

    @Test
    void testRoundTrip() {
        final Args args = Args.of(
                "text", "Hello world_1-2",
                "loading", true,
                "disabled", false,
                "tags", List.of("x y", "z"),
                "colour", "#ff4785",
                "rgba", "rgba(255, 0, 0, 0.5)",
                "hsla", "hsla(120, 50%, 25%, 0.25)",
                "when", "2026-10-05T12:30:00Z",
                "nothing", "");
        final Args decoded = ArgsCodec.decode(ArgsCodec.encode(args));

        assertThat(decoded.getString("text")).isEqualTo("Hello world_1-2");
        assertThat(decoded.get("loading")).isEqualTo(true);
        assertThat(decoded.get("disabled")).isEqualTo(false);
        assertThat(decoded.getList("tags")).containsExactly("x y", "z");
        assertThat(decoded.getString("colour")).isEqualTo("#ff4785");
        assertThat(decoded.getString("rgba")).isEqualTo("rgba(255, 0, 0, 0.5)");
        assertThat(decoded.getString("hsla")).isEqualTo("hsla(120, 50%, 25%, 0.25)");
        assertThat(decoded.getString("when")).isEqualTo("2026-10-05T12:30:00Z");
        assertThat(decoded.getString("nothing")).isEmpty();
    }

    @Test
    void testDecode_numbersAreStrings() {
        // The arg's type converts them, see TestArgType
        assertThat(ArgsCodec.decode("count:5").get("count")).isEqualTo("5");
        assertThat(ArgsCodec.decode("count:-1.5").get("count")).isEqualTo("-1.5");
    }

    @Test
    void testDecode_nullAndUndefined() {
        final Args decoded = ArgsCodec.decode("text:!null;other:!undefined;loading:!true");
        // Both are kept as null, so they unset the initial args they're merged into
        assertThat(decoded.getNames()).containsExactly("text", "other", "loading");
        assertThat(decoded.has("text")).isFalse();
        assertThat(decoded.has("other")).isFalse();
        assertThat(decoded.getBoolean("loading")).isTrue();

        final Args merged = Args.of("text", "a", "other", "b").merge(decoded);
        assertThat(merged.has("text")).isFalse();
        assertThat(merged.has("other")).isFalse();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "!hex(ff0000)|#ff0000",
            "!hex(F00)|#F00",
            "!rgba(255,0,0,0.5)|rgba(255, 0, 0, 0.5)",
            "!rgb(255,0,0)|rgb(255, 0, 0)",
            "!hsla(120,50,25,0.25)|hsla(120, 50%, 25%, 0.25)",
            "!hsl(120,50,25)|hsl(120, 50%, 25%)",
            "!RGB(1,2,3)|RGB(1, 2, 3)",
            "!date(2026-10-05T12:30:00.000Z)|2026-10-05T12:30:00.000Z",
            "Click+me|Click me",
            "Click%20me|Click me",
            "!false|false",
    })
    void testDecode_specialValues(final String encoded, final String expected) {
        assertThat(ArgsCodec.decode("value:" + encoded).getString("value")).isEqualTo(expected);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Not safe
            "text:%3Cscript%3Ealert(1)%3C%2Fscript%3E",
            "text:<script>alert(1)</script>",
            "%3Cscript%3E:x",
            "a.b:x",
            "text:100%zz",
            "text:caf%C3%A9",
            // Special values that don't give a safe value
            "colour:!hex(xyz)",
            "colour:!rgba(1,2,3)",
            "when:!date(%3Cb%3E)",
            "value:!other",
            // A list with an unsafe item
            "tags[0]:a;tags[1]:%3Cb%3E",
            // Object notation
            "obj[key]:x",
    })
    void testDecode_unsafeArgsAreLeftOut(final String encoded) {
        assertThat(ArgsCodec.decode(encoded).size()).isZero();
    }

    @Test
    void testDecode_unsafeArgsDontAffectOthers() {
        final Args decoded = ArgsCodec.decode("text:%3Cscript%3E;ok:yes");
        assertThat(decoded.getNames()).containsExactly("ok");
    }

    @Test
    void testDecode_escapedBracketIsPartOfTheName() {
        // Regression: the name was unescaped before looking for '[', so this was the list a=[v].
        // As in Storybook, it is the name 'a[0]', which is unsafe.
        assertThat(ArgsCodec.decode("a%5B0%5D:v").size()).isZero();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Items are placed by index, regardless of order
            "t[1]:b;t[0]:a|a,b",
            // Gaps are left out
            "t[0]:a;t[5]:z|a,z",
            // A plain value is appended to a list, as with picoquery's arrayRepeat
            "t[1]:b;t[0]:a;t:x;t[5]:z|a,b,x,z",
            // An index replaces a plain value
            "t:x;t[0]:a|a",
            // A repeated plain name gives a list
            "t:x;t:y|x,y",
            "t[]:a;t[]:b|a,b",
            "t:x;t[]:y|x,y",
            // The last value for an index wins
            "t[0]:a;t[0]:b|b",
            // Null and undefined items are left out
            "t[0]:a;t[1]:!null;t[2]:!undefined|a",
    })
    void testDecode_lists(final String encoded, final String expected) {
        assertThat(ArgsCodec.decode(encoded).getList("t")).containsExactly(expected.split(","));
    }

    @Test
    void testDecode_singleListItem() {
        assertThat(ArgsCodec.decode("t[]:a").get("t")).isEqualTo(List.of("a"));
    }

    @Test
    void testDecode_storybookUrl() {
        final Args decoded = ArgsCodec.decode("loading:!false;variant:contained-primary;tags[0]:a;tags[1]:b");
        assertThat(decoded.getNames()).containsExactly("loading", "variant", "tags");
        assertThat(decoded.get("loading")).isEqualTo(false);
        assertThat(decoded.getString("variant")).isEqualTo("contained-primary");
        assertThat(decoded.getList("tags")).containsExactly("a", "b");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {";", ";;", ":nokey", "t[x:a", "t[99999999999]:a"})
    void testDecode_malformedIsIgnored(final String encoded) {
        assertThat(ArgsCodec.decode(encoded).size()).isZero();
    }

    @Test
    void testDecode_nameWithoutValue() {
        // As in Storybook, the value is empty
        assertThat(ArgsCodec.decode("novalue").getString("novalue")).isEmpty();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "%41|A",
            "a%2Cb|a,b",
            "a+b|a b",
            "%2B|+",
            // Regression: Integer.parseInt accepted a sign, so '%-1' was decoded as a character
            "%-1|%-1",
            "%+1|% 1",
            // Malformed escapes leave the whole value as it is
            "100%zz|100%zz",
            "%41%2|%41%2",
            // Non-ASCII bytes are left escaped
            "%C3%A9|%C3%A9",
    })
    void testUnescape(final String value, final String expected) {
        assertThat(ArgsCodec.unescape(value)).isEqualTo(expected);
    }
}
