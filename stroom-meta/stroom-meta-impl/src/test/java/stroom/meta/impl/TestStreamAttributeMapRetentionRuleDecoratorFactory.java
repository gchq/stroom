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

package stroom.meta.impl;

import stroom.data.retention.api.DataRetentionRulesProvider;
import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.docstore.api.DocFinder;
import stroom.expression.matcher.ExpressionMatcher;
import stroom.expression.matcher.ExpressionMatcherFactory;
import stroom.meta.impl.StreamAttributeMapRetentionRuleDecoratorFactory.StreamAttributeMapRetentionRuleDecorator;
import stroom.meta.shared.DataRetentionFields;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.Status;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.mock.MockSecurityContext;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class TestStreamAttributeMapRetentionRuleDecoratorFactory {

    @Mock
    private ExpressionMatcherFactory expressionMatcherFactory;
    @Mock
    private DataRetentionRulesProvider rulesProvider;
    @Mock
    private DocFinder docFinder;

    @Test
    void testRulesAreReadOnce() {
        Mockito.when(rulesProvider.get()).thenReturn(Optional.of(rules(foreverRule())));

        final StreamAttributeMapRetentionRuleDecorator decorator = createFactory().createDecorator();
        for (int id = 1; id <= 3; id++) {
            assertThat(decorate(decorator, "Events"))
                    .containsEntry(DataRetentionFields.RETENTION_AGE, DataRetentionRule.FOREVER)
                    .hasEntrySatisfying(DataRetentionFields.RETENTION_RULE, rule ->
                            assertThat(rule).contains("Keep events"));
        }

        Mockito.verify(rulesProvider, Mockito.times(1)).get();
    }

    @Test
    void testEachDecoratorReadsTheRules() {
        Mockito.when(rulesProvider.get())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(rules(foreverRule())));
        final StreamAttributeMapRetentionRuleDecoratorFactory factory = createFactory();

        // So that a rule added since is used by the next request
        assertThat(decorate(factory.createDecorator(), "Events"))
                .containsEntry(DataRetentionFields.RETENTION_RULE, "None");
        assertThat(decorate(factory.createDecorator(), "Events"))
                .hasEntrySatisfying(DataRetentionFields.RETENTION_RULE, rule ->
                        assertThat(rule).contains("Keep events"));
    }

    @Test
    void testNoRuleMatches() {
        Mockito.when(rulesProvider.get()).thenReturn(Optional.of(rules(foreverRule())));

        assertThat(decorate(createFactory().createDecorator(), "Error"))
                .containsEntry(DataRetentionFields.RETENTION_AGE, DataRetentionRule.FOREVER)
                .containsEntry(DataRetentionFields.RETENTION_UNTIL, DataRetentionRule.FOREVER)
                .containsEntry(DataRetentionFields.RETENTION_RULE, "None");
    }

    private StreamAttributeMapRetentionRuleDecoratorFactory createFactory() {
        Mockito.when(expressionMatcherFactory.create(MetaFields.getFieldMap())).thenReturn(
                new ExpressionMatcher(MetaFields.getFieldMap()));
        return new StreamAttributeMapRetentionRuleDecoratorFactory(
                expressionMatcherFactory,
                rulesProvider,
                new StreamAttributeMapConverter(() -> docFinder),
                new MockSecurityContext());
    }

    private static Map<String, String> decorate(final StreamAttributeMapRetentionRuleDecorator decorator,
                                                final String typeName) {
        final Map<String, String> attributes = new HashMap<>();
        decorator.addMatchingRetentionRuleInfo(
                Meta.builder()
                        .id(1)
                        .typeName(typeName)
                        .status(Status.UNLOCKED)
                        .createMs(0L)
                        .build(),
                attributes);
        return attributes;
    }

    private static DataRetentionRules rules(final DataRetentionRule rule) {
        return DataRetentionRules.builder()
                .uuid("uuid")
                .rules(List.of(rule))
                .build();
    }

    private static DataRetentionRule foreverRule() {
        return DataRetentionRule.foreverRule(
                1,
                0L,
                "Keep events",
                true,
                ExpressionOperator.builder()
                        .addTextTerm(MetaFields.TYPE, Condition.EQUALS, "Events")
                        .build());
    }
}
