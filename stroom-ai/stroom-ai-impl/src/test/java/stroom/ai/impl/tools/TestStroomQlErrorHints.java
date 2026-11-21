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

package stroom.ai.impl.tools;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/// The hints a failed StroomQL query's error gets for SQL habits Stroom doesn't share, read with Stroom's own
/// tokeniser (seen with Haiku in eval cases 71 to 73).
class TestStroomQlErrorHints {

    @Test
    void aClauseAfterSelectThatGoesBeforeItIsNamed() {
        assertThat(StroomQlErrorHints.of("from E select UserId, count() as N group by UserId"))
                .isEqualTo(StroomQlErrorHints.CLAUSE_ORDER);
        assertThat(StroomQlErrorHints.of("from E select UserId sort by UserId"))
                .isEqualTo(StroomQlErrorHints.CLAUSE_ORDER);
        assertThat(StroomQlErrorHints.of("from E select UserId limit 5"))
                .isEqualTo(StroomQlErrorHints.CLAUSE_ORDER);
        // Every clause but show, as Sonnet wrote where after select in eval case 72.
        assertThat(StroomQlErrorHints.of("from E select UserId where Action = Logon"))
                .isEqualTo(StroomQlErrorHints.CLAUSE_ORDER);
        assertThat(StroomQlErrorHints.of("from E select UserId eval x = 1"))
                .isEqualTo(StroomQlErrorHints.CLAUSE_ORDER);
        assertThat(StroomQlErrorHints.of("from E select UserId having x > 1"))
                .isEqualTo(StroomQlErrorHints.CLAUSE_ORDER);
        assertThat(StroomQlErrorHints.CLAUSE_ORDER)
                .isEqualTo(" In StroomQL, every clause but show goes before select: from, join lines, where, eval, "
                           + "window, filter, sort by, group by, having, limit, select, show.");
        // Show is the one clause after select.
        assertThat(StroomQlErrorHints.of("from E select UserId show table")).isEmpty();
    }

    @Test
    void orderByIsNamedOutsideQuotedText() {
        assertThat(StroomQlErrorHints.of("from E select UserId order by UserId"))
                .isEqualTo(" StroomQL uses sort by, not order by.");
        assertThat(StroomQlErrorHints.of("from E group by UserId select UserId, count() as N ORDER  BY N desc"))
                .isEqualTo(" StroomQL uses sort by, not order by.");
        // Both.
        assertThat(StroomQlErrorHints.of("from E select UserId group by UserId order by UserId"))
                .isEqualTo(StroomQlErrorHints.CLAUSE_ORDER + " StroomQL uses sort by, not order by.");
    }

    @Test
    void aBetweenWithoutAndIsNamed() {
        // Sonnet wrote between "a,b" in eval cases 72 and 73, and Stroom said only "Expected between and".
        assertThat(StroomQlErrorHints.of("from E where EventTime between \"2026-01-05T00:00:00Z,2026-01-12T00:00:00Z\" "
                                         + "select UserId"))
                .isEqualTo(StroomQlErrorHints.BETWEEN_AND);
        assertThat(StroomQlErrorHints.BETWEEN_AND)
                .isEqualTo(" between takes two values joined by and, e.g. EventTime between \"2026-01-05T00:00:00Z\" "
                           + "and \"2026-01-12T00:00:00Z\".");
        assertThat(StroomQlErrorHints.of("from E where EventTime between day() - 1d and day() select UserId"))
                .isEmpty();
        assertThat(StroomQlErrorHints.of("from E where Name = \"between\" select UserId")).isEmpty();
    }

    @Test
    void aFunctionInSortByOrHavingIsNamed() {
        // Seen with Haiku and Sonnet in eval case 75: "Syntax exception, expected string" and "Expected field
        // string" said nothing of why.
        assertThat(StroomQlErrorHints.of("from E group by hr.Department sort by sum(Files) desc "
                                         + "select hr.Department, sum(Files) as Files"))
                .isEqualTo(StroomQlErrorHints.FIELD_NOT_FUNCTION);
        assertThat(StroomQlErrorHints.of("from E group by UserId having count() > 1 select UserId"))
                .isEqualTo(StroomQlErrorHints.FIELD_NOT_FUNCTION);
        assertThat(StroomQlErrorHints.FIELD_NOT_FUNCTION)
                .isEqualTo(" sort by and having take a field or a name given in eval or select, not a function, "
                           + "e.g. eval n = count() ... sort by n desc.");
        // A name, or a function elsewhere, is fine.
        assertThat(StroomQlErrorHints.of("from E eval n = count() group by UserId sort by n desc having n > 1 "
                                         + "select UserId, n")).isEmpty();
        assertThat(StroomQlErrorHints.of("from E where EventTime > day() - 1d sort by EventTime select x")).isEmpty();
    }

    @Test
    void theHintsFollowTheErrorAsASentence() {
        assertThat(StroomQlErrorHints.appendTo("Unexpected token GROUP after SELECT", "from E select a group by a"))
                .isEqualTo("Unexpected token GROUP after SELECT." + StroomQlErrorHints.CLAUSE_ORDER);
        assertThat(StroomQlErrorHints.appendTo("Bad. ", "from E select a order by a"))
                .isEqualTo("Bad. StroomQL uses sort by, not order by.");
        assertThat(StroomQlErrorHints.appendTo("Unknown field 'Hst'", "from E select Hst"))
                .isEqualTo("Unknown field 'Hst'");
    }

    @Test
    void aJoinLineAfterAnotherClauseIsNamed() {
        // Seen with Sonnet in eval case 71: "Unexpected token JOIN after WHERE".
        assertThat(StroomQlErrorHints.of("from E where Action = Export join \"HR\" as hr on UserId = hr.Username "
                                         + "select hr.Department"))
                .isEqualTo(" Join lines go straight after from.");
        assertThat(StroomQlErrorHints.of("from E eval x = 1 left join H as h on x = h.x select x"))
                .isEqualTo(" Join lines go straight after from.");
        // Join lines one after another, with keys joined by and, and a bracketed query's own, are where they go.
        assertThat(StroomQlErrorHints.of("from E join H as h on a = h.a and b = h.b semi join (from G where x = 1 "
                                         + "select y) as g on c = g.y where Action = Export select a")).isEmpty();
    }

    @Test
    void aQueryWithoutThoseHabitsGetsNoHint() {
        assertThat(StroomQlErrorHints.of("from E where Name = 'order by' group by UserId sort by UserId limit 5 "
                                         + "select UserId")).isEmpty();
        assertThat(StroomQlErrorHints.of("from E where Name = \"x\" select Order, By")).isEmpty();
        // A bracketed query's own clauses don't count.
        assertThat(StroomQlErrorHints.of("from E where UserId in (from H select Username) group by UserId "
                                         + "select UserId")).isEmpty();
        assertThat(StroomQlErrorHints.of("from \"unterminated")).isEmpty();
        assertThat(StroomQlErrorHints.of(null)).isEmpty();
    }
}
