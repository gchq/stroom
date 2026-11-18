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

package stroom.ai.impl.tools.validation;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomXPath {

    @ParameterizedTest
    @ValueSource(strings = {
            "fold-left(data/@value, 0, function($a, $b) { $a + $b })",
            "string-join(for-each(data/@value, upper-case#1), ',')",
            "fn:filter(data, function($d) { $d/@name = 'user' })",
            "array:fold-left([1, 2], 0, $add)",
            "map:for-each($m, $f)",
            "for-each-pair($a, $b, $f)",
            "``[user `{data[@name='user']/@value}`]``"})
    void whatStroomsSaxonCantRunIsSaidWithWhatToUseInstead(final String xpath) {
        // Each checked against Saxon-HE 9.9.1-8, Stroom's: "...require support for higher-order-functions, which
        // needs Saxon-PE or higher".
        assertThat(StroomXPath.unsupported(xpath)).hasValueSatisfying(why -> assertThat(why)
                .startsWith("Stroom's XSLT engine (Saxon-HE 9.9) has no higher-order functions")
                .contains("xsl:for-each-group").contains("not an option in Stroom"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "let $u := data[@name='user']/@value return upper-case($u)",
            "distinct-values(data[@name='user']/@value) ! lower-case(.)",
            "for $u in distinct-values(data/@value) return (data[@value = $u])[1]",
            "sort(data/@value)",
            "data[@name='for-each(']/@value",
            "concat('fold-left(', 'x')",
            "map:get($m, 'k')",
            "data[@name='function']/@value",
            "'a#1'"})
    void whatItCanRunIsLeftAlone(final String xpath) {
        assertThat(StroomXPath.unsupported(xpath)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"XTSE0010 Inline functions require support for higher-order-functions, which needs "
                            + "Saxon-PE or higher", "Named function references require support for "
                                                    + "higher-order-functions, which needs Saxon-PE or higher"})
    void saxonsCallForAHigherEditionIsSaidInStroomsTerms(final String message) {
        assertThat(StroomXPath.explained(message)).startsWith(message)
                .contains("Saxon-HE 9.9) has no higher-order functions").contains("xsl:iterate");
        assertThat(StroomXPath.explained("XPST0017 Cannot find a function named nosuch")).isEqualTo(
                "XPST0017 Cannot find a function named nosuch");
    }
}
