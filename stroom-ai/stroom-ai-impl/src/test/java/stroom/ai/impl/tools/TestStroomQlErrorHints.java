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
                .isEqualTo(" In StroomQL, group by, sort by and limit go before select.");
        assertThat(StroomQlErrorHints.of("from E select UserId sort by UserId"))
                .isEqualTo(" In StroomQL, group by, sort by and limit go before select.");
        assertThat(StroomQlErrorHints.of("from E select UserId limit 5"))
                .isEqualTo(" In StroomQL, group by, sort by and limit go before select.");
    }

    @Test
    void orderByIsNamedOutsideQuotedText() {
        assertThat(StroomQlErrorHints.of("from E select UserId order by UserId"))
                .isEqualTo(" StroomQL uses sort by, not order by.");
        assertThat(StroomQlErrorHints.of("from E group by UserId select UserId, count() as N ORDER  BY N desc"))
                .isEqualTo(" StroomQL uses sort by, not order by.");
        // Both.
        assertThat(StroomQlErrorHints.of("from E select UserId group by UserId order by UserId"))
                .isEqualTo(" In StroomQL, group by, sort by and limit go before select. StroomQL uses sort by, "
                           + "not order by.");
    }

    @Test
    void theHintsFollowTheErrorAsASentence() {
        assertThat(StroomQlErrorHints.appendTo("Unexpected token GROUP after SELECT", "from E select a group by a"))
                .isEqualTo("Unexpected token GROUP after SELECT. In StroomQL, group by, sort by and limit go before "
                           + "select.");
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
