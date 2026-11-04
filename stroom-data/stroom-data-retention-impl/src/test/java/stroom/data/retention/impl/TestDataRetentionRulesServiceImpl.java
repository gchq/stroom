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

package stroom.data.retention.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.docref.DocRef;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.StoreFactory;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestDataRetentionRulesServiceImpl {

    private static final DocRef FEED = new DocRef("Feed", "feed-uuid", "FEED");
    private static final DocRef NEW_FEED = new DocRef("Feed", "new-feed-uuid", "NEW_FEED");

    @Mock
    private StoreFactory storeFactory;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private Serialiser2Factory serialiser2Factory;
    @Mock
    private ClusterLockService clusterLockService;

    private DataRetentionRulesServiceImpl dataRetentionRulesService;

    @BeforeEach
    void setUp() {
        dataRetentionRulesService = new DataRetentionRulesServiceImpl(
                storeFactory,
                securityContext,
                serialiser2Factory,
                clusterLockService);
    }

    @Test
    void testDependencyRemapFunction_recordsExpressionReferences() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper();

        dataRetentionRulesService.getDependencyRemapFunction().remap(createDoc(), dependencyRemapper);

        assertThat(dependencyRemapper.getDependencies())
                .containsExactly(FEED);
    }

    @Test
    void testDependencyRemapFunction_remapsExpressionReferences() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper(Map.of(FEED, NEW_FEED));

        final DataRetentionRules remapped = dataRetentionRulesService.getDependencyRemapFunction()
                .remap(createDoc(), dependencyRemapper);

        final DataRetentionRule rule = remapped.getRules().getFirst();
        assertThat(rule.getExpression().getChildren())
                .singleElement()
                .isInstanceOfSatisfying(ExpressionTerm.class, term ->
                        assertThat(term.getDocRef()).isEqualTo(NEW_FEED));
        assertThat(rule.getName())
                .isEqualTo("Rule");
        assertThat(rule.getAge())
                .isEqualTo(30);
        assertThat(rule.getTimeUnit())
                .isEqualTo(TimeUnit.DAYS);
        assertThat(rule.isForever())
                .isFalse();
        assertThat(dependencyRemapper.isChanged())
                .isTrue();
    }

    @Test
    void testDependencyRemapFunction_noRules() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper();
        final DataRetentionRules doc = DataRetentionRules.builder()
                .uuid("rules-uuid")
                .name("Rules")
                .rules(List.of())
                .build();

        final DataRetentionRules remapped = dataRetentionRulesService.getDependencyRemapFunction()
                .remap(doc, dependencyRemapper);

        assertThat(remapped.getRules())
                .isEmpty();
        assertThat(dependencyRemapper.getDependencies())
                .isEmpty();
    }

    private DataRetentionRules createDoc() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder()
                        .field("Feed")
                        .condition(Condition.IS_DOC_REF)
                        .docRef(FEED)
                        .build())
                .build();
        final DataRetentionRule rule = DataRetentionRule.ageRule(
                1,
                0L,
                "Rule",
                true,
                expression,
                30,
                TimeUnit.DAYS);
        return DataRetentionRules.builder()
                .uuid("rules-uuid")
                .name("Rules")
                .rules(List.of(rule))
                .build();
    }
}
