/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.job.shared;

import stroom.util.json.JsonUtil;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Both criteria are optional in the API schema, so a conforming client may omit either or both.
 * {@code JobNodeResourceImpl.find} dereferences them without a null guard, so an omitted property
 * that deserialised to null answered HTTP 500.
 */
class TestFindJobNodeCriteria {

    @Test
    void omittedCriteriaDeserialiseToUnconstrained() {
        final FindJobNodeCriteria criteria = JsonUtil.readValue("{}", FindJobNodeCriteria.class);

        assertThat(criteria.getJobName())
                .as("an omitted jobName is unconstrained, not null")
                .isNotNull();
        assertThat(criteria.getJobName().isConstrained()).isFalse();
        assertThat(criteria.getNodeName())
                .as("an omitted nodeName is unconstrained, not null")
                .isNotNull();
        assertThat(criteria.getNodeName().isConstrained()).isFalse();
    }

    @Test
    void oneCriterionSuppliedAndOneOmitted() {
        // The exact body that answered 500.
        final FindJobNodeCriteria criteria = JsonUtil.readValue(
                "{\"jobName\":{\"string\":\"Data Delete\"}}", FindJobNodeCriteria.class);

        assertThat(criteria.getJobName().isConstrained())
                .as("the supplied criterion still constrains")
                .isTrue();
        assertThat(criteria.getJobName().getString()).isEqualTo("Data Delete");
        assertThat(criteria.getNodeName())
                .as("the omitted one is unconstrained, not null")
                .isNotNull();
        assertThat(criteria.getNodeName().isConstrained()).isFalse();
    }

    @Test
    void suppliedButEmptyCriteriaAreLeftAlone() {
        // The shape GWT sends, which always worked. It must keep working unchanged.
        final FindJobNodeCriteria criteria = JsonUtil.readValue(
                "{\"jobName\":{\"string\":\"Data Delete\"},\"nodeName\":{}}", FindJobNodeCriteria.class);

        assertThat(criteria.getJobName().getString()).isEqualTo("Data Delete");
        assertThat(criteria.getNodeName()).isNotNull();
        assertThat(criteria.getNodeName().isConstrained()).isFalse();
    }
}
