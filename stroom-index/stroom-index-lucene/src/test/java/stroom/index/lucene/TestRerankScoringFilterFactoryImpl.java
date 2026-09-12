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

import org.junit.jupiter.api.Test;

import stroom.ai.api.AiService;
import stroom.ai.api.OpenAIModelStore;
import stroom.query.common.v2.IndexFieldCache;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.ref.ErrorConsumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class TestRerankScoringFilterFactoryImpl {

    private final IndexFieldCache indexFieldCache = mock(IndexFieldCache.class);
    private final ErrorConsumer errorConsumer = mock(ErrorConsumer.class);
    private final RerankScoringFilterFactoryImpl factory = new RerankScoringFilterFactoryImpl(
            indexFieldCache,
            mock(OpenAIModelStore.class),
            mock(AiService.class));

    @Test
    void shouldIgnoreRerankValueFieldWithoutScoreField() {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("Vector__rerank_value");

        assertThat(factory.create(null, null, fieldIndex, null, errorConsumer)).isEmpty();
        verifyNoInteractions(indexFieldCache, errorConsumer);
    }

    @Test
    void shouldIgnoreRerankScoreFieldWithoutValueField() {
        final FieldIndex fieldIndex = new FieldIndex();
        fieldIndex.create("Vector__rerank_score");

        assertThat(factory.create(null, null, fieldIndex, null, errorConsumer)).isEmpty();
        verifyNoInteractions(indexFieldCache, errorConsumer);
    }
}
