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

package stroom.floormap.client.playback;

import stroom.query.api.token.BasicTokeniser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The point of these is the false positives, not the true ones.
///
/// Detecting `sort` where it really is a clause is easy. What earns the tokeniser its place
/// is *not* detecting it inside a quoted alias, a comment, a `jq` program, or after an
/// `=` — each of which a substring search would get wrong, and each of which would push the
/// Map tab onto the weaker time-comparison reduction for no reason.
class TestFloorMapEventsQueryOrder {

    // -----------------------------------------------------------------------
    // It is a clause
    // -----------------------------------------------------------------------

    @Test
    void testASortClauseIsDetected() {
        assertThat(FloorMapEventsQueryOrder.hasSortClause("""
                from people_events
                sort by EffectiveTime desc
                select Key, Value"""))
                .isTrue();
    }

    @Test
    void testASortClauseIsDetectedRegardlessOfCase() {
        assertThat(FloorMapEventsQueryOrder.hasSortClause("from x SORT BY Key select Key"))
                .isTrue();
    }

    @Test
    void testASortClauseAtTheVeryStartIsDetected() {
        assertThat(FloorMapEventsQueryOrder.hasSortClause("sort by Key")).isTrue();
    }

    @Test
    void testASortClauseFollowingACloseBracketIsDetected() {
        assertThat(FloorMapEventsQueryOrder.hasSortClause(
                "from x where (a = 1) sort by Key select Key"))
                .isTrue();
    }

    // -----------------------------------------------------------------------
    // It is not a clause — the cases that matter
    // -----------------------------------------------------------------------

    /// The default query, which must stay on the exact last-row-wins reduction.
    @Test
    void testTheGeneratedDefaultQueryHasNoSort() {
        assertThat(FloorMapEventsQueryOrder.hasSortClause(FloorMapEventsQuery.defaultQuery()))
                .isFalse();
    }

    /// The `[^=]` rule from the server-side tokeniser: after an `=` it is a field
    /// reference, not a clause.
    @Test
    void testSortAfterAnEqualsIsAFieldReferenceNotAClause() {
        assertThat(FloorMapEventsQueryOrder.hasSortClause("from x eval y = sort select y"))
                .isFalse();
    }

    /// A quoted column alias is a string, and this is why the tokeniser is used at all.
    @Test
    void testSortInsideAQuotedAliasIsNotAClause() {
        assertThat(FloorMapEventsQueryOrder.hasSortClause(
                "from x select Key as \"sort order\", Value"))
                .isFalse();
    }

    @Test
    void testSortInsideASingleQuotedValueIsNotAClause() {
        assertThat(FloorMapEventsQueryOrder.hasSortClause(
                "from x where Key = 'sort me' select Key"))
                .isFalse();
    }

    /// The shape a floor map's events query actually takes: a jq program in a quoted string.
    @Test
    void testSortInsideAJqProgramIsNotAClause() {
        assertThat(FloorMapEventsQueryOrder.hasSortClause(
                "from x select jq(Value, '.items | sort | .[0]') as \"First\""))
                .isFalse();
    }

    @Test
    void testSortInsideALineCommentIsNotAClause() {
        assertThat(FloorMapEventsQueryOrder.hasSortClause("""
                from x
                // we deliberately do not sort here
                select Key"""))
                .isFalse();
    }

    @Test
    void testSortInsideABlockCommentIsNotAClause() {
        assertThat(FloorMapEventsQueryOrder.hasSortClause(
                "from x /* sort by Key */ select Key"))
                .isFalse();
    }

    /// A longer word merely containing the letters.
    @Test
    void testSortAsPartOfALongerWordIsNotAClause() {
        assertThat(FloorMapEventsQueryOrder.hasSortClause(
                "from x where Resort = 'a' select sortCode, unsorted"))
                .isFalse();
    }

    // -----------------------------------------------------------------------
    // Degenerate input
    // -----------------------------------------------------------------------

    @Test
    void testNullAndBlankCountAsNoSort() {
        assertThat(FloorMapEventsQueryOrder.hasSortClause(null)).isFalse();
        assertThat(FloorMapEventsQueryOrder.hasSortClause("")).isFalse();
        assertThat(FloorMapEventsQueryOrder.hasSortClause("   \n  ")).isFalse();
    }

    /// An unterminated quote leaves its contents exposed to the scan, so a standalone `sort`
    /// inside one is detected.
    ///
    /// Verified rather than assumed: [BasicTokeniser] does *not* tag an unterminated
    /// single quote as a string — it leaves the span `UNKNOWN` — though it does still tag an
    /// unterminated block comment. Detecting here is the safe direction anyway: such a query fails
    /// server-side, so the only question is which reduction the client uses meanwhile, and the safer
    /// answer is the one that does not depend on an arrival order nobody can reason about.
    @Test
    void testAStandaloneSortInsideAnUnterminatedQuoteIsDetected() {
        assertThat(FloorMapEventsQueryOrder.hasSortClause("from x where Key = 'a sort by Key"))
                .isTrue();
    }

    /// An unterminated block comment is still masked, so its contents are not scanned.
    @Test
    void testSortInsideAnUnterminatedBlockCommentIsNotAClause() {
        assertThat(FloorMapEventsQueryOrder.hasSortClause("from x /* sort by Key")).isFalse();
    }

    // -----------------------------------------------------------------------
    // The entity id is the store Key
    // -----------------------------------------------------------------------

    /// The generated default binds it, which is the case that has to work: everything else about the
    /// ordering guarantee is moot if the out-of-the-box query does not qualify for it.
    @Test
    void testTheGeneratedDefaultQueryBindsTheEntityIdToTheKey() {
        assertThat(FloorMapEventsQueryOrder.bindsEntityIdToStoreKey(
                FloorMapEventsQuery.defaultQuery(), FloorMapEventsQuery.ENTITY_ID_COLUMN))
                .isTrue();
    }

    @Test
    void testABindingUnderACustomAliasIsRecognised() {
        assertThat(FloorMapEventsQueryOrder.bindsEntityIdToStoreKey(
                "from x select Key as \"Person\", Value", "Person"))
                .isTrue();
    }

    /// Single quotes are as good as double for an alias.
    @Test
    void testASingleQuotedAliasIsRecognised() {
        assertThat(FloorMapEventsQueryOrder.bindsEntityIdToStoreKey(
                "from x select Key as 'Entity ID'", "Entity ID"))
                .isTrue();
    }

    /// The case the check exists for.
    ///
    /// An entity id taken from the value scatters one entity's history across many key prefixes,
    /// so the last row to arrive is the last *prefix's* latest rather than the entity's. Note
    /// the `'.key'` inside the `jq` program: a textual search would match on it, which
    /// is why this scans tokens.
    @Test
    void testAnEntityIdDerivedFromTheValueIsNotABinding() {
        assertThat(FloorMapEventsQueryOrder.bindsEntityIdToStoreKey(
                "from x select jq(Value, '.key') as \"Entity ID\"", "Entity ID"))
                .isFalse();
    }

    /// A longer field name merely ending in the letters.
    @Test
    void testAFieldNameEndingInKeyIsNotABinding() {
        assertThat(FloorMapEventsQueryOrder.bindsEntityIdToStoreKey(
                "from x select EntityKey as \"Entity ID\"", "Entity ID"))
                .isFalse();
    }

    /// `Key` is bound, but to a different column than the document reads.
    @Test
    void testABindingToAnotherColumnDoesNotCount() {
        assertThat(FloorMapEventsQueryOrder.bindsEntityIdToStoreKey(
                "from x select Key as \"Something Else\", Value", "Entity ID"))
                .isFalse();
    }

    /// `Key` is selected but not aliased to the configured column, which is another query.
    ///
    /// Phrased so the scan actually runs over more than one token — `from x select Key,
    /// Value` is a single token, so it would pass without exercising anything.
    @Test
    void testAnUnaliasedKeyIsNotABinding() {
        assertThat(FloorMapEventsQueryOrder.bindsEntityIdToStoreKey(
                "from x select Key, jq(Value, '.a') as \"Entity ID\"", "Entity ID"))
                .isFalse();
    }

    @Test
    void testNullAndBlankInputsAreNotABinding() {
        assertThat(FloorMapEventsQueryOrder.bindsEntityIdToStoreKey(null, "Entity ID")).isFalse();
        assertThat(FloorMapEventsQueryOrder.bindsEntityIdToStoreKey("from x select Key as \"a\"",
                null)).isFalse();
        assertThat(FloorMapEventsQueryOrder.bindsEntityIdToStoreKey("from x select Key as \"a\"",
                "  ")).isFalse();
    }
}
