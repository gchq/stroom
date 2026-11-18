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

package stroom.query.common.v2;

import stroom.docref.DocRef;
import stroom.query.api.JoinKey;
import stroom.query.api.JoinType;
import stroom.query.api.Query;
import stroom.query.api.QueryJoin;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestJoinPlan {

    private static final Query QUERY = Query.builder()
            .dataSource(new DocRef("DataConnection", "events", "events"))
            .alias("e")
            .joins(List.of(new QueryJoin(
                    JoinType.LEFT,
                    "hr",
                    Query.builder().dataSource(new DocRef("DataConnection", "hr", "hr")).build(),
                    null,
                    List.of(new JoinKey("${e.UserId}", "${hr.Username}")),
                    null)))
            .build();

    @Test
    void joinedAndFirstSourceAliasFieldsAreAliased() {
        assertThat(JoinPlan.isAliasedField(QUERY, "hr.Department")).isTrue();
        assertThat(JoinPlan.isAliasedField(QUERY, "e.UserId")).isTrue();
    }

    @Test
    void plainFieldsAreNotAliased() {
        assertThat(JoinPlan.isAliasedField(QUERY, "UserId")).isFalse();
        assertThat(JoinPlan.isAliasedField(QUERY, "hrx.Department")).isFalse();
        assertThat(JoinPlan.isAliasedField(QUERY, "hr.")).isFalse();
        assertThat(JoinPlan.isAliasedField(null, "hr.Department")).isFalse();
        assertThat(JoinPlan.isAliasedField(Query.builder().build(), "hr.Department")).isFalse();
    }

    @Test
    void misspeltFirstSourceAliasFieldStillLeavesAPlainFieldToWarnAbout() {
        // The search of the first source is asked for the field the alias names, so a data source
        // still warns about a misspelt field through its plain name.
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("e.Usr");
        final List<JoinPlan> plans = JoinPlan.createAll(QUERY, fieldIndex, new ExpressionContext(), Map.of());
        final Coprocessors coprocessors = Mockito.mock(Coprocessors.class);
        Mockito.when(coprocessors.getFieldIndex()).thenReturn(fieldIndex);
        JoiningCoprocessors.create(coprocessors, QUERY, plans, List.of(Mockito.mock(JoinTable.class)));

        assertThat(fieldIndex.getFields()).contains("Usr");
        assertThat(JoinPlan.isAliasedField(QUERY, "Usr")).isFalse();
    }
}
