/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.data.retention.impl;


import stroom.data.retention.shared.DataRetentionRule;
import stroom.data.retention.shared.DataRetentionRules;
import stroom.data.shared.StreamTypeNames;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.MetaFields;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.json.JsonUtil;
import stroom.util.shared.time.TimeUnit;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

class TestDataRetentionPolicySerialisation {

    @Test
    void test() throws Exception {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder();
        builder.addTextTerm(MetaFields.TYPE, Condition.EQUALS, StreamTypeNames.RAW_EVENTS);
        builder.addDocRefTerm(MetaFields.FEED, Condition.IS_DOC_REF, DocRef
                .builder()
                .type(FeedDoc.TYPE)
                .uuid("test")
                .name("TEST_FEED")
                .build());
        final ExpressionOperator expression = builder.build();

        final List<DataRetentionRule> list = new ArrayList<>();
        list.add(createRule(1, expression, 10, TimeUnit.DAYS));
        list.add(createRule(2, expression, 1, TimeUnit.MONTHS));
        list.add(createRule(3, expression, 2, TimeUnit.WEEKS));

        final DataRetentionRules policies = DataRetentionRules.builder()
                .uuid(UUID.randomUUID().toString())
                .rules(list)
                .build();

        final String json = JsonUtil.getMapper().writeValueAsString(policies);

        System.out.println(json);
    }

    private DataRetentionRule createRule(final int num,
                                         final ExpressionOperator expression,
                                         final int age,
                                         final TimeUnit timeUnit) {
        return new DataRetentionRule(num,
                System.currentTimeMillis(),
                "rule " + num,
                true,
                expression,
                age,
                timeUnit,
                false);
    }
}
