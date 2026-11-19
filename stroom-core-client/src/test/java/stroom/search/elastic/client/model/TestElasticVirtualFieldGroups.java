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

package stroom.search.elastic.client.model;

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
    private static final ExpressionOperator MAIL = ExpressionOperator.builder()
            .addTerm("host", Condition.EQUALS, "mail*")
            .build();
    private static final List<ElasticVirtualFieldGroup> GROUPS = List.of(
            new ElasticVirtualFieldGroup("web", WEB),
            new ElasticVirtualFieldGroup("other", null));

    @Test
    void testAdd() {
        final List<ElasticVirtualFieldGroup> groups = ElasticVirtualFieldGroups.add(GROUPS, " mail ", MAIL);

        assertThat(groups).hasSize(3);
        assertThat(groups.get(2).getName()).isEqualTo("mail");
        assertThat(groups.get(2).getExpression()).isEqualTo(MAIL);
        assertThat(GROUPS).hasSize(2);
    }

    @Test
    void testAddToNoGroups() {
        assertThat(ElasticVirtualFieldGroups.add(null, "mail", MAIL))
                .extracting(ElasticVirtualFieldGroup::getName)
                .containsExactly("mail");
    }

    @Test
    void testAddRejectsInvalidName() {
        assertThat(ElasticVirtualFieldGroups.add(GROUPS, " ", MAIL)).isEqualTo(GROUPS);
        assertThat(ElasticVirtualFieldGroups.add(GROUPS, null, MAIL)).isEqualTo(GROUPS);
        assertThat(ElasticVirtualFieldGroups.add(GROUPS, "web", MAIL)).isEqualTo(GROUPS);
    }

    @Test
    void testRemove() {
        assertThat(ElasticVirtualFieldGroups.remove(GROUPS, 0))
                .extracting(ElasticVirtualFieldGroup::getName)
                .containsExactly("other");
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
    void testValidateNameForNewGroup() {
        assertThat(ElasticVirtualFieldGroups.validateName(GROUPS, -1, "web"))
                .isEqualTo(ElasticVirtualFieldGroups.ERROR_NAME_IN_USE);
        assertThat(ElasticVirtualFieldGroups.validateName(GROUPS, -1, ""))
                .isEqualTo(ElasticVirtualFieldGroups.ERROR_EMPTY_NAME);
        assertThat(ElasticVirtualFieldGroups.validateName(null, -1, "mail")).isNull();
    }

    @Test
    void testReplace() {
        final List<ElasticVirtualFieldGroup> groups = ElasticVirtualFieldGroups.replace(GROUPS, 1, " mail ", MAIL);

        assertThat(groups.get(0)).isEqualTo(GROUPS.get(0));
        assertThat(groups.get(1).getName()).isEqualTo("mail");
        assertThat(groups.get(1).getExpression()).isEqualTo(MAIL);
        assertThat(GROUPS.get(1).getName()).isEqualTo("other");
    }

    @Test
    void testReplaceKeepingName() {
        final List<ElasticVirtualFieldGroup> groups = ElasticVirtualFieldGroups.replace(GROUPS, 0, "web", MAIL);

        assertThat(groups.get(0).getName()).isEqualTo("web");
        assertThat(groups.get(0).getExpression()).isEqualTo(MAIL);
    }

    @Test
    void testReplaceRejectsInvalidNameOrIndex() {
        assertThat(ElasticVirtualFieldGroups.replace(GROUPS, 1, "web", MAIL)).isEqualTo(GROUPS);
        assertThat(ElasticVirtualFieldGroups.replace(GROUPS, 1, " ", MAIL)).isEqualTo(GROUPS);
        assertThat(ElasticVirtualFieldGroups.replace(GROUPS, 2, "mail", MAIL)).isEqualTo(GROUPS);
        assertThat(ElasticVirtualFieldGroups.replace(GROUPS, -1, "mail", MAIL)).isEqualTo(GROUPS);
    }
}
