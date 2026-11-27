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

package stroom.ai.impl.tools.query;

import stroom.ai.impl.tools.query.StroomQlLimit.Change;
import stroom.ai.impl.tools.query.StroomQlLimit.Limited;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestStroomQlLimit {

    @Test
    void aQueryWithoutALimitIsGivenOneBeforeSelect() {
        final Limited limited = StroomQlLimit.apply("from \"TWITTER\" where Text = \"*sky*\" select Text", 100);

        assertThat(limited.change()).isEqualTo(Change.ADDED);
        assertThat(limited.query()).isEqualTo("from \"TWITTER\" where Text = \"*sky*\" limit 100\nselect Text");
    }

    @Test
    void aQueryWithoutSelectIsGivenOneAtTheEnd() {
        final Limited limited = StroomQlLimit.apply("from \"TWITTER\"\n", 100);

        assertThat(limited.query()).isEqualTo("from \"TWITTER\"\nlimit 100");
    }

    @Test
    void aLimitOverTheMostIsLoweredAndOneWithinItIsKept() {
        assertThat(StroomQlLimit.apply("from x limit 500 select a", 100))
                .isEqualTo(new Limited("from x limit 100 select a", Change.LOWERED));
        assertThat(StroomQlLimit.apply("from x LIMIT 50 select a", 100))
                .isEqualTo(new Limited("from x LIMIT 50 select a", Change.NONE));
        // One number for each level of grouping, each capped.
        assertThat(StroomQlLimit.apply("from x group by a limit 50, 5000 select a", 100).query())
                .isEqualTo("from x group by a limit 50, 100 select a");
        // A number too large to read is over too.
        assertThat(StroomQlLimit.apply("from x limit 99999999999999999999 select a", 100).query())
                .isEqualTo("from x limit 100 select a");
    }

    @Test
    void textInStringsAndCommentsIsNotTakenForALimit() {
        final Limited limited = StroomQlLimit.apply("from x // limit 5\nwhere a = \"limit 5\" select a", 100);

        assertThat(limited.change()).isEqualTo(Change.ADDED);
        assertThat(limited.query()).isEqualTo("from x // limit 5\nwhere a = \"limit 5\" limit 100\nselect a");
        // Numbers after the limit's own, e.g. in a later clause, are left alone.
        assertThat(StroomQlLimit.apply("from x limit 10 select a show table(5000)", 100).query())
                .isEqualTo("from x limit 10 select a show table(5000)");
    }

    @Test
    void aLimitIsAddedAtTheTopLevelNotInsideASubquery() {
        // The where subquery's select isn't the query's: Stroom refuses a limit inside it.
        final Limited limited = StroomQlLimit.apply(
                "from Events where UserId in (from People select Name) select UserId", 100);

        assertThat(limited.change()).isEqualTo(Change.ADDED);
        assertThat(limited.query()).isEqualTo(
                "from Events where UserId in (from People select Name) limit 100\nselect UserId");
        // With no top-level select, the limit goes at the end, outside the bracket.
        assertThat(StroomQlLimit.apply("from Events where UserId in (from People select Name)", 100).query())
                .isEqualTo("from Events where UserId in (from People select Name)\nlimit 100");
    }

    @Test
    void aJoinedQuerysOwnLimitIsNotTakenForTheQuerys() {
        final String query = "from Events\n"
                             + "join (from People limit 5000 select Name) as p on UserId = p.Name\n"
                             + "select UserId";
        final Limited limited = StroomQlLimit.apply(query, 100);

        // The joined query keeps its own limit; the outer search is capped.
        assertThat(limited.change()).isEqualTo(Change.ADDED);
        assertThat(limited.query()).isEqualTo("from Events\n"
                                              + "join (from People limit 5000 select Name) as p on UserId = p.Name\n"
                                              + "limit 100\nselect UserId");
        // A top-level limit after a joined query's is the one lowered.
        assertThat(StroomQlLimit.apply("from Events join (from People limit 50 select Name) as p "
                                       + "on UserId = p.Name limit 500 select UserId", 100).query())
                .isEqualTo("from Events join (from People limit 50 select Name) as p "
                           + "on UserId = p.Name limit 100 select UserId");
    }

    @Test
    void aQueryThatCantBeReadIsLeftForStroomToRefuse() {
        assertThat(StroomQlLimit.apply("   ", 100)).isEqualTo(new Limited("   ", Change.NONE));
    }
}
