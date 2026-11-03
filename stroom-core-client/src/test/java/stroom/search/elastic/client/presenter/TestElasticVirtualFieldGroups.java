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

package stroom.search.elastic.client.presenter;

import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.search.elastic.shared.ElasticVirtualFieldGroup;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TestElasticVirtualFieldGroups {

    private static final ExpressionOperator WEB = ExpressionOperator.builder()
            .addTerm("host", Condition.EQUALS, "web*")
            .build();
    private static final List<ElasticVirtualFieldGroup> GROUPS = List.of(
            new ElasticVirtualFieldGroup("web", WEB),
            new ElasticVirtualFieldGroup(ElasticVirtualFieldGroups.NEW_GROUP_NAME, null));

    @Test
    void testAddGivesUniqueNameAndEmptyExpression() {
        final List<ElasticVirtualFieldGroup> groups = ElasticVirtualFieldGroups.add(GROUPS);

        assertThat(groups).hasSize(3);
        assertThat(groups.get(2).getName()).isEqualTo(ElasticVirtualFieldGroups.NEW_GROUP_NAME + " 2");
        assertThat(groups.get(2).getExpression()).isEqualTo(ExpressionOperator.builder().build());
        assertThat(GROUPS).hasSize(2);
    }

    @Test
    void testAddToNoGroups() {
        assertThat(ElasticVirtualFieldGroups.add(null))
                .extracting(ElasticVirtualFieldGroup::getName)
                .containsExactly(ElasticVirtualFieldGroups.NEW_GROUP_NAME);
    }

    @Test
    void testRemove() {
        assertThat(ElasticVirtualFieldGroups.remove(GROUPS, 0))
                .extracting(ElasticVirtualFieldGroup::getName)
                .containsExactly(ElasticVirtualFieldGroups.NEW_GROUP_NAME);
        assertThat(ElasticVirtualFieldGroups.remove(GROUPS, 5)).isEqualTo(GROUPS);
        assertThat(ElasticVirtualFieldGroups.remove(GROUPS, -1)).isEqualTo(GROUPS);
    }

    @Test
    void testValidateName() {
        assertThat(ElasticVirtualFieldGroups.validateName(GROUPS, 1, " "))
                .isEqualTo(ElasticVirtualFieldGroups.ERROR_EMPTY_NAME);
        assertThat(ElasticVirtualFieldGroups.validateName(GROUPS, 1, "web"))
                .isEqualTo(ElasticVirtualFieldGroups.ERROR_NAME_IN_USE);
        assertThat(ElasticVirtualFieldGroups.validateName(GROUPS, 0, " web ")).isNull();
        assertThat(ElasticVirtualFieldGroups.validateName(GROUPS, 1, "mail")).isNull();
    }

    @Test
    void testRenameKeepsExpression() {
        final List<ElasticVirtualFieldGroup> groups = ElasticVirtualFieldGroups.rename(GROUPS, 0, " websites ");

        assertThat(groups.get(0).getName()).isEqualTo("websites");
        assertThat(groups.get(0).getExpression()).isEqualTo(WEB);
        assertThat(ElasticVirtualFieldGroups.rename(GROUPS, 1, "web")).isEqualTo(GROUPS);
        assertThat(ElasticVirtualFieldGroups.rename(GROUPS, 9, "other")).isEqualTo(GROUPS);
    }

    @Test
    void testSetExpression() {
        final List<ElasticVirtualFieldGroup> groups = ElasticVirtualFieldGroups.setExpression(GROUPS, 1, WEB);

        assertThat(groups.get(1).getName()).isEqualTo(ElasticVirtualFieldGroups.NEW_GROUP_NAME);
        assertThat(groups.get(1).getExpression()).isEqualTo(WEB);
        assertThat(ElasticVirtualFieldGroups.setExpression(GROUPS, 2, WEB)).isEqualTo(GROUPS);
    }
}
