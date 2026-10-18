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

import stroom.ai.impl.tools.StroomQlLimit.Change;
import stroom.ai.impl.tools.StroomQlLimit.Limited;

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
    void aQueryThatCantBeReadIsLeftForStroomToRefuse() {
        assertThat(StroomQlLimit.apply("   ", 100)).isEqualTo(new Limited("   ", Change.NONE));
    }
}
