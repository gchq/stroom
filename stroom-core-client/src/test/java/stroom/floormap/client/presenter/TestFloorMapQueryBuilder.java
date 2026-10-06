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

package stroom.floormap.client.presenter;

import stroom.floormap.client.model.FloorMapFactHistory;
import stroom.floormap.client.playback.FloorMapHistogramBuckets;
import stroom.floormap.shared.FloorMapFieldMapping;
import stroom.floormap.shared.FloorMapFieldMapping.Role;
import stroom.floormap.shared.ValueFormat;
import stroom.query.api.token.QuotedStringUtil;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/// Tests for [FloorMapQueryBuilder] — verifying that the generated
/// StroomQL queries and column aliases are correct for both JSON and XML
/// value formats.
class TestFloorMapQueryBuilder {

    // ---- buildFactsQuery (JSON) ----

    @Test
    void testBuildFactsQuery_json_singleMapping() {
        final List<FloorMapFieldMapping> schema = List.of(
                new FloorMapFieldMapping(".type", Role.TYPE, "Type", null));

        final String query = FloorMapQueryBuilder.buildFactsQuery(
                schema, ValueFormat.JSON);

        assertThat(query).startsWith("from param('FactStore')");
        assertThat(query).contains("Key");
        assertThat(query).contains("EffectiveTime");
        assertThat(query).contains("jq(Value, \".type\") as \"type\"");
    }

    @Test
    void testBuildFactsQuery_selectsTheStatusColumnLikeAnyOtherRole() {
        // The Map tab reads status from this column, so a mapped status must be selected - and
        // under a custom path, from that path.
        final String query = FloorMapQueryBuilder.buildFactsQuery(
                List.of(new FloorMapFieldMapping(".type", Role.TYPE, "Type", null),
                        new FloorMapFieldMapping(".lifecycle", Role.STATUS, "Status", null)),
                ValueFormat.JSON);

        assertThat(query).contains("jq(Value, \".lifecycle\") as \"lifecycle\"");
    }

    @Test
    void testBuildFactsQuery_selectsTheRawMillisecondColumn() {
        // The alias is the contract between this builder and FloorMapFactHistory, which is in
        // another module and matches the column back by name. Asserted against the constant
        // rather than a literal so a rename cannot break the pair silently.
        final String query = FloorMapQueryBuilder.buildFactsQuery(
                List.of(new FloorMapFieldMapping(".type", Role.TYPE, "Type", null)),
                ValueFormat.JSON);

        assertThat(query).contains(
                "toLong(EffectiveTime) as \"" + FloorMapFactHistory.EFFECTIVE_TIME_MS_COLUMN + "\"");
    }

    @Test
    void testBuildFactsQuery_keepsTheReadableEffectiveTimeColumnToo() {
        // The Events Query tab shows these columns to the user, so the raw millis is an addition
        // rather than a replacement.
        final String query = FloorMapQueryBuilder.buildFactsQuery(
                List.of(new FloorMapFieldMapping(".type", Role.TYPE, "Type", null)),
                ValueFormat.JSON);

        assertThat(query).contains("  EffectiveTime, ");
    }

    @Test
    void testBuildFactsQuery_millisecondColumnIsPresentForXmlToo() {
        final String query = FloorMapQueryBuilder.buildFactsQuery(
                List.of(new FloorMapFieldMapping("/entry/type", Role.TYPE, "Type", null)),
                ValueFormat.XML);

        assertThat(query).contains(
                "toLong(EffectiveTime) as \"" + FloorMapFactHistory.EFFECTIVE_TIME_MS_COLUMN + "\"");
    }

    @Test
    void testBuildFactsQuery_json_multipleMappings() {
        final List<FloorMapFieldMapping> schema = List.of(
                new FloorMapFieldMapping(".type", Role.TYPE, "Type", null),
                new FloorMapFieldMapping(
                        ".coords", Role.POSITION, "Position", null),
                new FloorMapFieldMapping(
                        ".img", Role.IMAGE, "Image", null));

        final String query = FloorMapQueryBuilder.buildFactsQuery(
                schema, ValueFormat.JSON);

        assertThat(query).contains("jq(Value, \".type\") as \"type\"");
        assertThat(query).contains("jq(Value, \".coords\") as \"coords\"");
        assertThat(query).contains("jq(Value, \".img\") as \"img\"");
    }

    @Test
    void testBuildFactsQuery_json_hyphenatedKey() {
        final List<FloorMapFieldMapping> schema = List.of(
                new FloorMapFieldMapping(
                        ".tm-world-to-map", Role.WORLD_TO_MAP,
                        "World to Map", null));

        final String query = FloorMapQueryBuilder.buildFactsQuery(
                schema, ValueFormat.JSON);

        // Hyphenated keys need quoting in jq — with the leading dot for field
        // access (a bare quoted string is a jq literal, not a lookup).
        assertThat(query).contains(
                "jq(Value, \".\\\"tm-world-to-map\\\"\") as \"tm-world-to-map\"");
    }

    @Test
    void testBuildFactsQuery_json_skipsNullPath() {
        final List<FloorMapFieldMapping> schema = List.of(
                new FloorMapFieldMapping(
                        null, Role.TYPE, "Type", null),
                new FloorMapFieldMapping(
                        ".coords", Role.POSITION, "Position", null));

        final String query = FloorMapQueryBuilder.buildFactsQuery(
                schema, ValueFormat.JSON);

        // Should only contain the coords mapping, not type
        assertThat(query).doesNotContain("type");
        assertThat(query).contains("jq(Value, \".coords\") as \"coords\"");
    }

    @Test
    void testBuildFactsQuery_json_skipsEmptyPath() {
        final List<FloorMapFieldMapping> schema = List.of(
                new FloorMapFieldMapping(
                        "", Role.TYPE, "Type", null),
                new FloorMapFieldMapping(
                        ".coords", Role.POSITION, "Position", null));

        final String query = FloorMapQueryBuilder.buildFactsQuery(
                schema, ValueFormat.JSON);

        assertThat(query).doesNotContain("as \"type\"");
        assertThat(query).contains("jq(Value, \".coords\") as \"coords\"");
    }

    // ---- buildFactsQuery (XML) ----

    @Test
    void testBuildFactsQuery_xml_singleMapping() {
        final List<FloorMapFieldMapping> schema = List.of(
                new FloorMapFieldMapping(
                        "/entry/type", Role.TYPE, "Type", null));

        final String query = FloorMapQueryBuilder.buildFactsQuery(
                schema, ValueFormat.XML);

        assertThat(query).startsWith("from param('FactStore')");
        assertThat(query).contains(
                "xpath(Value, \"/entry/type\") as \"entry/type\"");
    }

    @Test
    void testBuildFactsQuery_xml_attributePath() {
        final List<FloorMapFieldMapping> schema = List.of(
                new FloorMapFieldMapping(
                        "/entry/@type", Role.TYPE, "Type", null));

        final String query = FloorMapQueryBuilder.buildFactsQuery(
                schema, ValueFormat.XML);

        assertThat(query).contains(
                "xpath(Value, \"/entry/@type\") as \"entry/@type\"");
    }

    // ---- buildExtractExpression ----

    @Test
    void testBuildExtractExpression_json_simplePath() {
        final String expr = FloorMapQueryBuilder.buildExtractExpression(
                ".type", ValueFormat.JSON);
        assertThat(expr).isEqualTo("jq(Value, \".type\")");
    }

    @Test
    void testBuildExtractExpression_json_hyphenatedPath() {
        final String expr = FloorMapQueryBuilder.buildExtractExpression(
                ".tm-world-to-map", ValueFormat.JSON);
        assertThat(expr).isEqualTo(
                "jq(Value, \".\\\"tm-world-to-map\\\"\")");
    }

    @Test
    void testBuildExtractExpression_xml_elementPath() {
        final String expr = FloorMapQueryBuilder.buildExtractExpression(
                "/entry/type", ValueFormat.XML);
        assertThat(expr).isEqualTo("xpath(Value, \"/entry/type\")");
    }

    @Test
    void testBuildExtractExpression_xml_attributePath() {
        final String expr = FloorMapQueryBuilder.buildExtractExpression(
                "/entry/@id", ValueFormat.XML);
        assertThat(expr).isEqualTo("xpath(Value, \"/entry/@id\")");
    }

    // ---- buildColumnAlias ----

    @Test
    void testBuildColumnAlias_json_simplePath() {
        final String alias = FloorMapQueryBuilder.buildColumnAlias(
                ".type", ValueFormat.JSON);
        assertThat(alias).isEqualTo("type");
    }

    @Test
    void testBuildColumnAlias_json_keyUsedVerbatim() {
        final String alias = FloorMapQueryBuilder.buildColumnAlias(
                ".tm-world-to-map", ValueFormat.JSON);
        assertThat(alias)
                .as("the key is used verbatim; hyphen mangling made .a-b and .a_b collide")
                .isEqualTo("tm-world-to-map");
    }

    @Test
    void testBuildColumnAlias_xml_pathWithoutLeadingSlash() {
        final String alias = FloorMapQueryBuilder.buildColumnAlias(
                "/entry/type", ValueFormat.XML);
        assertThat(alias).isEqualTo("entry/type");
    }

    @Test
    void testBuildColumnAlias_xml_nestedPath() {
        final String alias = FloorMapQueryBuilder.buildColumnAlias(
                "/entry/nested/prop", ValueFormat.XML);
        assertThat(alias)
                .as("the whole path, so /entry/prop and /entry/nested/prop stay distinct")
                .isEqualTo("entry/nested/prop");
    }

    @Test
    void testBuildColumnAlias_xml_attributePathKeepsTheAt() {
        final String alias = FloorMapQueryBuilder.buildColumnAlias(
                "/entry/@type", ValueFormat.XML);
        assertThat(alias)
                .as("the @ is kept, so /entry/type and /entry/@type stay distinct")
                .isEqualTo("entry/@type");
    }

    @Test
    void testBuildColumnAlias_xml_singleSegment() {
        final String alias = FloorMapQueryBuilder.buildColumnAlias(
                "type", ValueFormat.XML);
        assertThat(alias).isEqualTo("type");
    }
    // ---- interpolated paths are escaped for the enclosing StroomQL literal ----

    /// A schema path containing a double quote must not close the StroomQL literal
    /// it is interpolated into.
    ///
    /// Schema paths come from the Settings grid, so this is ordinary user input
    /// rather than a hostile edge case. Unescaped, `.a"b` produced
    /// `jq(Value, ".a"b")` — the literal ends at the second quote and the rest
    /// is stray tokens, so the whole query fails to parse.
    @Test
    void testBuildExtractExpression_json_escapesQuoteInPath() {
        final String expr = FloorMapQueryBuilder.buildExtractExpression(
                ".a\"b", ValueFormat.JSON);

        assertThat(expr)
                .as("the quote must be escaped, not left to terminate the literal")
                .isEqualTo("jq(Value, \".\\\"a\\\\\\\"b\\\"\")");
        assertUnescapesTo(expr, "jq(Value, ", ".\"a\\\"b\"");
    }

    /// An XPath may legitimately contain quotes — a predicate such as
    /// `/entry[@type="gate"]` is perfectly ordinary — so the XML branch has to
    /// escape them too.
    @Test
    void testBuildExtractExpression_xml_escapesQuotesInXPath() {
        final String expr = FloorMapQueryBuilder.buildExtractExpression(
                "/entry[@type=\"gate\"]", ValueFormat.XML);

        assertThat(expr).isEqualTo(
                "xpath(Value, \"/entry[@type=\\\"gate\\\"]\")");
        assertUnescapesTo(expr, "xpath(Value, ", "/entry[@type=\"gate\"]");
    }

    /// A backslash in a path is escaped so it survives unescaping intact.
    @Test
    void testBuildExtractExpression_xml_escapesBackslash() {
        final String expr = FloorMapQueryBuilder.buildExtractExpression(
                "/entry/a\\b", ValueFormat.XML);
        assertUnescapesTo(expr, "xpath(Value, ", "/entry/a\\b");
    }

    /// Ordinary paths are unchanged, so the common case reads as before.
    @Test
    void testBuildExtractExpression_ordinaryPathsAreUnchanged() {
        assertThat(FloorMapQueryBuilder.buildExtractExpression(".type", ValueFormat.JSON))
                .isEqualTo("jq(Value, \".type\")");
        assertThat(FloorMapQueryBuilder.buildExtractExpression("/entry/type", ValueFormat.XML))
                .isEqualTo("xpath(Value, \"/entry/type\")");
    }

    /// Extracts the quoted literal from `prefix"..."` and asserts that
    /// unescaping it — exactly as the query tokeniser does — recovers
    /// `expected`. Checking the round trip rather than only the literal text
    /// proves the escaping is actually correct rather than merely different.
    private static void assertUnescapesTo(final String expression,
                                          final String prefix,
                                          final String expected) {
        assertThat(expression).startsWith(prefix + "\"");
        assertThat(expression).endsWith("\")");
        final String literal = expression.substring(
                prefix.length(), expression.length() - 1);
        final char[] chars = literal.toCharArray();
        assertThat(QuotedStringUtil.unescape(chars, 0, chars.length - 1, '\\'))
                .as("tokeniser view of " + literal)
                .isEqualTo(expected);
    }

    // ---- the alias is injective, and safe once emitted ----

    /// Distinct paths must give distinct aliases, for every collision the old derivation
    /// allowed.
    ///
    /// This is the assertion that matters. The consumer looks a column up *by*
    /// alias, so two paths sharing one alias do not merely produce an odd heading — two
    /// roles resolve to the same column index and the map draws with the wrong data,
    /// silently.
    @Test
    void testBuildColumnAlias_isInjective() {
        // JSON: the old hyphen-to-underscore mangle collapsed these two.
        assertThat(FloorMapQueryBuilder.buildColumnAlias(".a-b", ValueFormat.JSON))
                .isNotEqualTo(FloorMapQueryBuilder.buildColumnAlias(".a_b", ValueFormat.JSON));

        // XML: taking only the last segment collapsed all of these.
        final String a = FloorMapQueryBuilder.buildColumnAlias("/entry/type", ValueFormat.XML);
        final String b = FloorMapQueryBuilder.buildColumnAlias("/entry/meta/type", ValueFormat.XML);
        final String c = FloorMapQueryBuilder.buildColumnAlias("/other/type", ValueFormat.XML);
        final String d = FloorMapQueryBuilder.buildColumnAlias("/entry/@type", ValueFormat.XML);
        assertThat(List.of(a, b, c, d)).doesNotHaveDuplicates();
    }

    /// A path containing characters that would break an unquoted identifier still produces
    /// valid query text, because the alias is quoted where it is emitted.
    ///
    /// The old code claimed to produce a "SQL-safe" alias but only replaced hyphens, and
    /// only for JSON — so a path of `.my key` yielded the bare alias `my key`
    /// and the whole generated query failed to parse.
    @Test
    void testBuildFactsQuery_aliasWithSpaceIsQuoted() {
        final String query = FloorMapQueryBuilder.buildFactsQuery(
                List.of(new FloorMapFieldMapping(".my key", Role.TYPE, "My Key", null)),
                ValueFormat.JSON);

        assertThat(query).contains(" as \"my key\"");
        assertUnescapesTo(lastQuotedLiteral(query), "my key");
    }

    /// A quote in the path cannot terminate the alias literal early.
    @Test
    void testBuildFactsQuery_aliasWithQuoteIsEscaped() {
        final String query = FloorMapQueryBuilder.buildFactsQuery(
                List.of(new FloorMapFieldMapping(".a\"b", Role.TYPE, "A B", null)),
                ValueFormat.JSON);

        assertThat(query).contains(" as \"a\\\"b\"");
        assertUnescapesTo(lastQuotedLiteral(query), "a\"b");
    }

    /// An XML `text()` path — which the old derivation emitted verbatim and unquoted,
    /// producing `as text()` — is now a quoted alias.
    @Test
    void testBuildFactsQuery_xmlFunctionCallPathIsQuoted() {
        final String query = FloorMapQueryBuilder.buildFactsQuery(
                List.of(new FloorMapFieldMapping("/entry/name/text()", Role.LABEL, "Name", null)),
                ValueFormat.XML);

        assertThat(query).contains(" as \"entry/name/text()\"");
    }

    /// The alias the query carries is exactly the one the consumer matches against.
    @Test
    void testEmittedAliasUnescapesToTheBareAlias() {
        for (final String path : List.of(".type", ".tm-world-to-map", ".my key", ".a\"b")) {
            final String bare = FloorMapQueryBuilder.buildColumnAlias(path, ValueFormat.JSON);
            final String query = FloorMapQueryBuilder.buildFactsQuery(
                    List.of(new FloorMapFieldMapping(path, Role.TYPE, "T", null)),
                    ValueFormat.JSON);
            assertUnescapesTo(lastQuotedLiteral(query), bare);
        }
    }

    /// The text of the last quoted literal in `query`, including its quotes.
    private static String lastQuotedLiteral(final String query) {
        final int end = query.lastIndexOf('"');
        final int start = query.lastIndexOf(" as \"") + " as ".length();
        assertThat(start).isGreaterThan(0);
        return query.substring(start, end + 1);
    }

    /// Asserts the tokeniser reads `literal` back as `expected`.
    private static void assertUnescapesTo(final String literal, final String expected) {
        final char[] chars = literal.toCharArray();
        assertThat(QuotedStringUtil.unescape(chars, 0, chars.length - 1, '\\'))
                .as("tokeniser view of " + literal)
                .isEqualTo(expected);
    }


    @Test
    void theDefaultHistogramQueryGroupsServerSide() {
        final String query = FloorMapQueryBuilder.defaultHistogramQuery();

        // One row per bucket rather than one per event is the whole point, and it is the part that
        // must survive a user editing this query.
        assertThat(query).contains("group by bucket");
        assertThat(query).contains("select bucket, count()");

        // The width is a parameter, not substituted text: it changes on every zoom, and rewriting a
        // query the user may have edited on every range change is what that would cost.
        assertThat(query).contains("floorTime(EffectiveTime, param('bucketWidth'))");

        // Ordered, so the client can place counts without sorting them itself.
        assertThat(query).contains("sort by bucket");

        // Named by the document's store reference, so no user-authored SQL is parsed or rewritten.
        assertThat(query).startsWith("from param('EventStore')");
    }

    @Test
    void theDefaultHistogramQueryClausesAreInStroomQlOrder() {
        // from [where] [eval] [group by] [having] [sort by] [limit] select - select comes last,
        // which is the ordering mistake that is easiest to make and produces a parse error.
        final String query = FloorMapQueryBuilder.defaultHistogramQuery();
        assertThat(query.indexOf("from ")).isLessThan(query.indexOf("eval "));
        assertThat(query.indexOf("eval ")).isLessThan(query.indexOf("group by "));
        assertThat(query.indexOf("group by ")).isLessThan(query.indexOf("sort by "));
        assertThat(query.indexOf("sort by ")).isLessThan(query.indexOf("select "));
    }

    @Test
    void theBucketWidthParameterNameMatchesTheOneThePresenterSends() {
        // The query and the parameter are set in two places; this is what keeps them the same one.
        assertThat(FloorMapQueryBuilder.defaultHistogramQuery())
                .contains("param('" + FloorMapQueryBuilder.PARAM_BUCKET_WIDTH + "')");
    }

    /// Every width the ladder can produce must be a duration `floorTime` accepts.
    ///
    /// The query no longer embeds the width, so this checks the ladder itself rather than the
    /// query text: an ISO-8601 duration of digits and unit letters, which is what
    /// `Duration.parse` takes.
    @Test
    void everyWidthOnTheLadderIsAnIsoDuration() {
        final long[] ranges = {0L, 3600_000L, 86_400_000L, 30L * 86_400_000L, 400L * 86_400_000L};
        for (final long range : ranges) {
            final String iso = FloorMapHistogramBuckets.durationFor(range);
            // Parsed rather than pattern-matched: a regex admits P1W and P1M, which floorTime's
            // Duration.parse rejects, so matching one would prove nothing about what the store sees.
            assertThatCode(() -> Duration.parse(iso))
                    .as("range %d gives %s", range, iso)
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void theDefaultExtentQueryAsksForTwoValuesAndNoBound() {
        final String query = FloorMapQueryBuilder.defaultExtentQuery();

        // One row of two aggregates, not a set of buckets: the extent is the exact first and last
        // event times, and its size does not depend on how long the store has been running.
        assertThat(query).contains("min(EffectiveTime), max(EffectiveTime)");
        assertThat(query).startsWith("from param('EventStore')");

        // Unbounded, which is what separates it from the histogram. A bound here could never reach
        // data earlier than what is already shown - the one thing "Show All" exists to do.
        assertThat(query).doesNotContain("where");
    }

    /// The extent must be grouped, and the grouped column must be selected.
    ///
    /// This test previously asserted the opposite - a bare
    /// `select min(EffectiveTime), max(EffectiveTime)` - because the design note recorded
    /// "aggregates with no group by yield a single row" as an assumption to confirm at runtime, and
    /// nobody confirmed it. Run against a real store, that query returns one row *per event*
    /// with `min == max`: every row is its own group. The timeline then collapsed to a
    /// zero-width range and reported "No events in this time range", with Show All unable to fix it
    /// because it re-ran the same query.
    ///
    /// Grouping by a constant makes the whole store one group - but only if the grouped column is
    /// also selected. `group by allRows` without `allRows` in the `select` list
    /// does not aggregate either, which is the second half of the same trap.
    @Test
    void theDefaultExtentQueryCollapsesToASingleRow() {
        final String query = FloorMapQueryBuilder.defaultExtentQuery();

        assertThat(query).contains("group by allRows");
        assertThat(query)
                .as("the grouped column has to be selected or no grouping happens")
                .contains("select allRows,");
        assertThat(query.indexOf("eval allRows"))
                .as("the constant is evaluated before it is grouped on")
                .isLessThan(query.indexOf("group by allRows"));
    }
}
