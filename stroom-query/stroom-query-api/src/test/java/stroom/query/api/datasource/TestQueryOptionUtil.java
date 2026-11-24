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

package stroom.query.api.datasource;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestQueryOptionUtil {

    @Test
    void testResolve_nullSet() {
        assertThat(QueryOptionUtil.resolve(null, Map.of(QueryOptionSetFixtures.TOGGLE, "true")))
                .isEmpty();
    }

    @Test
    void testResolve_defaults() {
        final Map<String, String> resolved = QueryOptionUtil.resolve(QueryOptionSetFixtures.SET_A, null);

        assertThat(resolved)
                .containsExactly(
                        Map.entry(QueryOptionSetFixtures.TOGGLE, "false"),
                        Map.entry(QueryOptionSetFixtures.LEVEL, QueryOptionSetFixtures.LEVEL_OFF),
                        Map.entry(QueryOptionSetFixtures.LENGTH, "0"),
                        Map.entry(QueryOptionSetFixtures.SWITCH, "true"));
    }

    @Test
    void testResolve_storedValues() {
        final Map<String, String> resolved = QueryOptionUtil.resolve(QueryOptionSetFixtures.SET_A, Map.of(
                QueryOptionSetFixtures.LEVEL, "2",
                QueryOptionSetFixtures.LENGTH, "3",
                QueryOptionSetFixtures.SWITCH, "false"));

        assertThat(resolved)
                .containsEntry(QueryOptionSetFixtures.LEVEL, "2")
                .containsEntry(QueryOptionSetFixtures.LENGTH, "3")
                .containsEntry(QueryOptionSetFixtures.SWITCH, "false");
        assertThat(QueryOptionUtil.getInteger(resolved, QueryOptionSetFixtures.LENGTH))
                .isEqualTo(3);
        assertThat(QueryOptionUtil.getBoolean(resolved, QueryOptionSetFixtures.SWITCH))
                .isFalse();
    }

    @Test
    void testResolve_invalidValuesTakeDefaults() {
        final Map<String, String> resolved = QueryOptionUtil.resolve(QueryOptionSetFixtures.SET_B, Map.of(
                QueryOptionSetFixtures.MODE, "NONSENSE",
                QueryOptionSetFixtures.LEVEL, "3",
                QueryOptionSetFixtures.LIMIT, "100000",
                "unknownOption", "true"));

        assertThat(resolved)
                .containsEntry(QueryOptionSetFixtures.MODE, QueryOptionSetFixtures.MODE_NORMAL)
                .containsEntry(QueryOptionSetFixtures.LEVEL, QueryOptionSetFixtures.LEVEL_OFF)
                .containsEntry(QueryOptionSetFixtures.LIMIT, "50")
                .doesNotContainKey("unknownOption");
    }

    @Test
    void testResolve_disabledOptionTakesDefault() {
        // The options that depend on the level are disabled while it is off
        final Map<String, String> resolved = QueryOptionUtil.resolve(QueryOptionSetFixtures.SET_A, Map.of(
                QueryOptionSetFixtures.LENGTH, "4"));

        assertThat(resolved)
                .containsEntry(QueryOptionSetFixtures.LENGTH, "0");
        assertThat(QueryOptionUtil.getDisabledOptionIds(QueryOptionSetFixtures.SET_A, Map.of(
                QueryOptionSetFixtures.LENGTH, "4")))
                .containsExactlyInAnyOrder(
                        QueryOptionSetFixtures.LENGTH,
                        QueryOptionSetFixtures.SWITCH);
    }

    @Test
    void testResolve_toggleDisablesLevel() {
        final Map<String, String> options = Map.of(QueryOptionSetFixtures.TOGGLE, "true");

        assertThat(QueryOptionUtil.getDisabledOptionIds(QueryOptionSetFixtures.SET_A, options))
                .containsExactlyInAnyOrder(
                        QueryOptionSetFixtures.LEVEL,
                        QueryOptionSetFixtures.LENGTH,
                        QueryOptionSetFixtures.SWITCH);
        assertThat(QueryOptionUtil.resolve(QueryOptionSetFixtures.SET_A, options))
                .containsEntry(QueryOptionSetFixtures.TOGGLE, "true");
    }

    @Test
    void testResolve_levelDisablesToggle() {
        final Map<String, String> options = Map.of(
                QueryOptionSetFixtures.LEVEL,
                QueryOptionSetFixtures.LEVEL_AUTO);

        assertThat(QueryOptionUtil.getDisabledOptionIds(QueryOptionSetFixtures.SET_A, options))
                .containsExactly(QueryOptionSetFixtures.TOGGLE);
    }

    @Test
    void testResolve_mutuallyExclusiveBothSet_laterOptionWins() {
        // The options dialog prevents this, but stored values could still hold both
        final Map<String, String> resolved = QueryOptionUtil.resolve(QueryOptionSetFixtures.SET_A, Map.of(
                QueryOptionSetFixtures.TOGGLE, "true",
                QueryOptionSetFixtures.LEVEL, "1"));

        assertThat(resolved)
                .containsEntry(QueryOptionSetFixtures.TOGGLE, "false")
                .containsEntry(QueryOptionSetFixtures.LEVEL, "1");
    }

    @Test
    void testResolve_strictModeCascades() {
        // Strict mode disables the level, which in turn disables the options that depend on it
        final Map<String, String> options = Map.of(
                QueryOptionSetFixtures.MODE, QueryOptionSetFixtures.MODE_STRICT,
                QueryOptionSetFixtures.SPREAD, "2",
                QueryOptionSetFixtures.JOIN, QueryOptionSetFixtures.JOIN_ALL,
                QueryOptionSetFixtures.LEVEL, "2",
                QueryOptionSetFixtures.LENGTH, "1",
                QueryOptionSetFixtures.LIMIT, "10");

        assertThat(QueryOptionUtil.resolve(QueryOptionSetFixtures.SET_B, options))
                .containsExactly(
                        Map.entry(QueryOptionSetFixtures.MODE, QueryOptionSetFixtures.MODE_STRICT),
                        Map.entry(QueryOptionSetFixtures.SPREAD, "2"),
                        Map.entry(QueryOptionSetFixtures.JOIN, QueryOptionSetFixtures.JOIN_ANY),
                        Map.entry(QueryOptionSetFixtures.LEVEL, QueryOptionSetFixtures.LEVEL_OFF),
                        Map.entry(QueryOptionSetFixtures.LENGTH, "0"),
                        Map.entry(QueryOptionSetFixtures.LIMIT, "50"));
    }

    @Test
    void testResolve_normalModeDisablesSpread() {
        final Set<String> disabled = QueryOptionUtil.getDisabledOptionIds(QueryOptionSetFixtures.SET_B, Map.of(
                QueryOptionSetFixtures.JOIN, QueryOptionSetFixtures.JOIN_ALL,
                QueryOptionSetFixtures.LEVEL, "1"));

        assertThat(disabled)
                .containsExactly(QueryOptionSetFixtures.SPREAD);
    }

    @Test
    void testNormalise_dropsDefaultsAndDisabled() {
        final Map<String, String> normalised = QueryOptionUtil.normalise(QueryOptionSetFixtures.SET_A, Map.of(
                QueryOptionSetFixtures.TOGGLE, "true",
                QueryOptionSetFixtures.SWITCH, "true",
                QueryOptionSetFixtures.LENGTH, "2"));

        assertThat(normalised)
                .containsExactly(Map.entry(QueryOptionSetFixtures.TOGGLE, "true"));
    }

    @Test
    void testNormalise_allDefaultsIsNull() {
        assertThat(QueryOptionUtil.normalise(QueryOptionSetFixtures.SET_B, Map.of(
                QueryOptionSetFixtures.MODE, QueryOptionSetFixtures.MODE_NORMAL)))
                .isNull();
        assertThat(QueryOptionUtil.normalise(QueryOptionSetFixtures.SET_B, null))
                .isNull();
        assertThat(QueryOptionUtil.normalise(null, Map.of(QueryOptionSetFixtures.TOGGLE, "true")))
                .isNull();
    }

    @Test
    void testHasNonDefaultOptions() {
        assertThat(QueryOptionUtil.hasNonDefaultOptions(QueryOptionSetFixtures.SET_A,
                Map.of(QueryOptionSetFixtures.TOGGLE, "true")))
                .isTrue();
        assertThat(QueryOptionUtil.hasNonDefaultOptions(QueryOptionSetFixtures.SET_A,
                Map.of(QueryOptionSetFixtures.TOGGLE, "false")))
                .isFalse();
        // Stored but disabled, so has no effect
        assertThat(QueryOptionUtil.hasNonDefaultOptions(QueryOptionSetFixtures.SET_A,
                Map.of(QueryOptionSetFixtures.LENGTH, "3")))
                .isFalse();
    }

    @Test
    void testGetInteger_missing() {
        assertThatThrownBy(() -> QueryOptionUtil.getInteger(Map.of(), QueryOptionSetFixtures.SPREAD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(QueryOptionSetFixtures.SPREAD);
    }
}
