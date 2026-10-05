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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestArgs {

    @Test
    void testOfAndGetters() {
        final Args args = Args.of("text", "Click me", "loading", true, "count", 5, "tags", List.of("a", "b"));

        assertThat(args.getString("text")).isEqualTo("Click me");
        assertThat(args.getBoolean("loading")).isTrue();
        // Integers are stored as doubles but shown without a decimal point
        assertThat(args.get("count")).isEqualTo(5.0);
        assertThat(args.getString("count")).isEqualTo("5");
        assertThat(args.getNumber("count", 0)).isEqualTo(5.0);
        assertThat(args.getList("tags")).containsExactly("a", "b");
        assertThat(args.getNames()).containsExactly("text", "loading", "count", "tags");
        assertThat(args.size()).isEqualTo(4);
    }

    @Test
    void testUndefined() {
        final Args args = Args.empty();
        assertThat(args.has("text")).isFalse();
        assertThat(args.getString("text")).isNull();
        assertThat(args.getString("text", "Close")).isEqualTo("Close");
        assertThat(args.getBoolean("loading")).isFalse();
        assertThat(args.getNumber("count", 7)).isEqualTo(7.0);
        assertThat(args.getList("tags")).isEmpty();
    }

    @Test
    void testWithAndMerge() {
        final Args base = Args.of("text", "Close", "loading", false);
        final Args changed = base.with("loading", true).with("text", null);

        assertThat(base.getBoolean("loading")).isFalse();
        assertThat(changed.getBoolean("loading")).isTrue();
        assertThat(changed.has("text")).isFalse();

        final Args merged = base.merge(Args.of("text", "Save", "variant", "primary"));
        assertThat(merged.getString("text")).isEqualTo("Save");
        assertThat(merged.getString("variant")).isEqualTo("primary");
        assertThat(merged.getBoolean("loading")).isFalse();
    }

    @Test
    void testDiff() {
        final Args initial = Args.of("text", "Close", "loading", false);
        final Args current = Args.of("text", "Save", "loading", false, "variant", "primary");

        assertThat(current.diff(initial)).isEqualTo(Args.of("text", "Save", "variant", "primary"));
        assertThat(initial.diff(initial).size()).isZero();
    }

    @Test
    void testGetNumber_notANumber() {
        assertThat(Args.of("count", "abc").getNumber("count", 3)).isEqualTo(3.0);
        assertThat(Args.of("count", "2.5").getNumber("count", 3)).isEqualTo(2.5);
    }

    @Test
    void testEquals() {
        assertThat(Args.of("a", 1)).isEqualTo(Args.of("a", 1.0)).hasSameHashCodeAs(Args.of("a", 1.0));
        assertThat(Args.of("a", 1)).isNotEqualTo(Args.of("a", 2));
    }

    @Test
    void testInvalid() {
        assertThatThrownBy(() -> Args.of("a")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Args.of("a", new Object())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testFormatNumber() {
        assertThat(Args.formatNumber(5)).isEqualTo("5");
        assertThat(Args.formatNumber(-2)).isEqualTo("-2");
        assertThat(Args.formatNumber(2.5)).isEqualTo("2.5");
        assertThat(Args.formatNumber(0)).isEqualTo("0");
        assertThat(Args.formatNumber(-0.0)).isEqualTo("0");
        assertThat(Args.formatNumber(0.1)).isEqualTo("0.1");
        assertThat(Args.formatNumber(-123.456)).isEqualTo("-123.456");
    }

    @Test
    void testFormatNumber_noScientificNotation() {
        // Regression: the JVM gave 1.0E20 and the browser 1e+20
        assertThat(Args.formatNumber(1e20)).isEqualTo("100000000000000000000");
        assertThat(Args.formatNumber(1e21)).isEqualTo("1000000000000000000000");
        assertThat(Args.formatNumber(1.5e16)).isEqualTo("15000000000000000");
        assertThat(Args.formatNumber(-2.5e-7)).isEqualTo("-0.00000025");
        assertThat(Args.formatNumber(1.2345e-3)).isEqualTo("0.0012345");
    }

    @Test
    void testFormatNumber_nonFinite() {
        assertThat(Args.formatNumber(Double.NaN)).isEmpty();
        assertThat(Args.formatNumber(Double.POSITIVE_INFINITY)).isEmpty();
        assertThat(Args.formatNumber(Double.NEGATIVE_INFINITY)).isEmpty();
    }

    @Test
    void testWithNull() {
        final Args initial = Args.of("text", "Click me", "loading", true);
        final Args merged = initial.merge(Args.empty().withNull("text"));

        assertThat(merged.has("text")).isFalse();
        assertThat(merged.get("text")).isNull();
        assertThat(merged.getNames()).containsExactly("text", "loading");
    }
}
