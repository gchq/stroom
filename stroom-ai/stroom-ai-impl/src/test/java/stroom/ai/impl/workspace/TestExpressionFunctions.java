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

package stroom.ai.impl.workspace;

import stroom.ai.impl.workspace.ExpressionFunctions.TextCall;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestExpressionFunctions {

    @Test
    void testCatalogueListsTheAvailableFunctionsByCategory() {
        final String catalogue = ExpressionFunctions.catalogue();

        assertThat(catalogue).contains("String: ").contains("Mathematics: ").contains("Date: ")
                .contains("Aggregate: ");
        assertThat(catalogue).contains("substring").contains("round").contains("formatDate").contains("sum");
        // Functions that take no arguments are shown called, so year() isn't taken for a date's year.
        assertThat(catalogue).contains("year()").contains("now()").doesNotContain("formatDate()");
        // Those that reach outside the table, or pick out rows of a group, aren't listed.
        assertThat(catalogue).doesNotContain("askAi").doesNotContain("getState").doesNotContain("first,")
                .doesNotContain("countGroups").doesNotContain("currentUser").doesNotContain("dashboard")
                .doesNotContain("param");
    }

    @Test
    void testEveryListedFunctionHasHelp() {
        for (final String line : ExpressionFunctions.catalogue().split("\n")) {
            final List<String> names = Arrays.asList(line.substring(line.indexOf(": ") + 2).split(", "));
            for (final String listed : names) {
                // One that takes no arguments is listed called, e.g. year().
                final String name = listed.replace("()", "");
                assertThat(ExpressionFunctions.help(List.of(listed)))
                        .as(name)
                        .startsWith(name)
                        .contains(name + "(")
                        .doesNotContain("isn't available")
                        .doesNotContain("There is no function");
            }
        }
    }

    @Test
    void testHelp() {
        final String help = ExpressionFunctions.help(List.of("SUBSTRING", "askAi", "substrin"));

        assertThat(help).contains("substring(");
        assertThat(help).contains("askAi isn't available here");
        assertThat(help).contains("There is no function 'substrin'. Did you mean substring");
        assertThat(ExpressionFunctions.help(List.of("countGroups"))).contains("isn't available here");
    }

    @Test
    void testClosestNames() {
        assertThat(ExpressionFunctions.closestNames("uppercse")).first().isEqualTo("upperCase");
        assertThat(ExpressionFunctions.closestNames("zzzzzzzzzzzz")).isEmpty();
        // Other languages' names give Stroom's, and a short name isn't taken for any short name nearby.
        assertThat(ExpressionFunctions.closestNames("avg")).containsExactly("average");
        assertThat(ExpressionFunctions.closestNames("len")).containsExactly("stringLength");
        assertThat(ExpressionFunctions.closestNames("qqq")).isEmpty();
    }

    @Test
    void testTextCallFindsAFunctionThatAlwaysGivesText() {
        assertThat(ExpressionFunctions.textCall("formatDate(floorHour(${EventTime}), 'yyyy-MM-dd HH:mm')"))
                .contains(new TextCall("formatDate", List.of("floorHour(${EventTime})", "'yyyy-MM-dd HH:mm'")));
        // Any case and spacing, as Stroom takes it.
        assertThat(ExpressionFunctions.textCall("  FORMATDATE ( ${EventTime} ) "))
                .contains(new TextCall("formatDate", List.of("${EventTime}")));
        // Commas and brackets in quoted text or nested calls don't split the arguments.
        assertThat(ExpressionFunctions.textCall("concat(${User}, ', (', max(${Host}, 'a,b'), ')')"))
                .contains(new TextCall("concat", List.of("${User}", "', ('", "max(${Host}, 'a,b')", "')'")));
        assertThat(ExpressionFunctions.textCall("replace(${Path}, \"\\\\\", '/')"))
                .map(TextCall::function).contains("replace");
    }

    @Test
    void testTextCallCoversTheStringFunctionsStroomHas() {
        // From Stroom's own function definitions, so new text functions are covered without change here.
        for (final String expression : List.of(
                "upperCase(${User})",
                "lowerCase(${User})",
                "substring(${User}, 0, 3)",
                "substringBefore(${User}, '@')",
                "substringAfter(${User}, '@')",
                "toString(${Count})",
                "formatDuration(${Duration})",
                "extractHostFromUri(${Url})",
                "hash(${User})",
                "decode(${Code}, '1', 'one', 'other')",
                "joining(${User})")) {
            assertThat(ExpressionFunctions.textCall(expression)).as(expression).isPresent();
        }
    }

    @Test
    void testTextCallIgnoresWhatMayNotBeText() {
        for (final String expression : List.of(
                // Functions giving numbers, dates or booleans.
                "floorHour(${EventTime})",
                "count()",
                "parseDate(${When}, 'yyyy-MM-dd')",
                "stringLength(${User})",
                "toDouble(${Count})",
                // Functions giving whatever their arguments are.
                "if(${A} = 1, 'yes', 'no')",
                "max(${Count})",
                // Not one call.
                "${EventTime}",
                "upperCase(${A}) + lowerCase(${B})",
                "'text'",
                "noSuchFunction(${A})",
                "formatDate(${EventTime}",
                "")) {
            assertThat(ExpressionFunctions.textCall(expression)).as(expression).isEmpty();
        }
        assertThat(ExpressionFunctions.textCall(null)).isEmpty();
    }
}
