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

package stroom.receive.rules.impl;

import stroom.cluster.lock.api.ClusterLockService;
import stroom.docref.DocRef;
import stroom.docstore.api.DependencyRemapper;
import stroom.docstore.api.Serialiser2Factory;
import stroom.docstore.api.StoreFactory;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.receive.rules.shared.ReceiveAction;
import stroom.receive.rules.shared.ReceiveDataRule;
import stroom.receive.rules.shared.ReceiveDataRules;
import stroom.security.api.SecurityContext;

import jakarta.inject.Provider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestReceiveDataRuleSetStoreImpl {

    private static final DocRef DICTIONARY = new DocRef("Dictionary", "dictionary-uuid", "Dictionary");
    private static final DocRef NEW_DICTIONARY = new DocRef("Dictionary", "new-dictionary-uuid", "New dictionary");

    @Mock
    private StoreFactory storeFactory;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private Serialiser2Factory serialiser2Factory;
    @Mock
    private Provider<StroomReceiptPolicyConfig> stroomReceiptPolicyConfigProvider;
    @Mock
    private ClusterLockService clusterLockService;

    private ReceiveDataRuleSetStoreImpl receiveDataRuleSetStore;

    @BeforeEach
    void setUp() {
        receiveDataRuleSetStore = new ReceiveDataRuleSetStoreImpl(
                storeFactory,
                securityContext,
                serialiser2Factory,
                stroomReceiptPolicyConfigProvider,
                clusterLockService);
    }

    @Test
    void testDependencyRemapFunction_recordsExpressionReferences() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper();

        receiveDataRuleSetStore.getDependencyRemapFunction().remap(createDoc(), dependencyRemapper);

        assertThat(dependencyRemapper.getDependencies())
                .containsExactly(DICTIONARY);
    }

    @Test
    void testDependencyRemapFunction_remapsExpressionReferences() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper(Map.of(DICTIONARY, NEW_DICTIONARY));

        final ReceiveDataRules remapped = receiveDataRuleSetStore.getDependencyRemapFunction()
                .remap(createDoc(), dependencyRemapper);

        final ReceiveDataRule rule = remapped.getRules().getFirst();
        assertThat(rule.getExpression().getChildren())
                .singleElement()
                .isInstanceOfSatisfying(ExpressionTerm.class, term ->
                        assertThat(term.getDocRef()).isEqualTo(NEW_DICTIONARY));
        assertThat(rule.getName())
                .isEqualTo("Rule");
        assertThat(rule.getAction())
                .isEqualTo(ReceiveAction.REJECT);
        assertThat(dependencyRemapper.isChanged())
                .isTrue();
    }

    @Test
    void testDependencyRemapFunction_noRules() {
        final DependencyRemapper dependencyRemapper = new DependencyRemapper();
        final ReceiveDataRules doc = ReceiveDataRules.builder()
                .uuid("rules-uuid")
                .name("Rules")
                .rules(List.of())
                .build();

        final ReceiveDataRules remapped = receiveDataRuleSetStore.getDependencyRemapFunction()
                .remap(doc, dependencyRemapper);

        assertThat(remapped.getRules())
                .isEmpty();
        assertThat(dependencyRemapper.getDependencies())
                .isEmpty();
    }

    private ReceiveDataRules createDoc() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addTerm(ExpressionTerm.builder()
                        .field("Feed")
                        .condition(Condition.IN_DICTIONARY)
                        .docRef(DICTIONARY)
                        .build())
                .build();
        final ReceiveDataRule rule = ReceiveDataRule.builder()
                .withRuleNumber(1)
                .withName("Rule")
                .withEnabled(true)
                .withExpression(expression)
                .withAction(ReceiveAction.REJECT)
                .build();
        return ReceiveDataRules.builder()
                .uuid("rules-uuid")
                .name("Rules")
                .rules(List.of(rule))
                .build();
    }
}
