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

package stroom.index.lucene;

import stroom.query.common.v2.ErrorConsumerImpl;
import stroom.query.language.functions.FieldIndex;
import stroom.test.common.util.test.StroomUnitTest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestRerankScoringFilterFactoryImpl extends StroomUnitTest {

    @Test
    void testMissingScoreField() {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("vector__rerank_value");
        final ErrorConsumerImpl errorConsumer = new ErrorConsumerImpl();

        final var filter = new RerankScoringFilterFactoryImpl(null, null, null)
                .create(null, null, fieldIndex, null, errorConsumer);

        assertThat(filter).isEmpty();
        assertThat(errorConsumer.hasErrors()).isFalse();
    }

    @Test
    void testMissingValueField() {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("vector__rerank_score");
        final ErrorConsumerImpl errorConsumer = new ErrorConsumerImpl();

        final var filter = new RerankScoringFilterFactoryImpl(null, null, null)
                .create(null, null, fieldIndex, null, errorConsumer);

        assertThat(filter).isEmpty();
        assertThat(errorConsumer.hasErrors()).isFalse();
    }
}
