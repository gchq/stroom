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

import stroom.docref.DocRef;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.query.api.datasource.DenseVectorFieldConfig.VectorSimilarityFunctionType;
import stroom.search.elastic.shared.ElasticDenseVectorSettings;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestElasticDenseVectorFormValues {

    private static final DocRef EMBEDDING_MODEL = new DocRef(OpenAIModelDoc.TYPE, "uuid-1", "embedding");
    private static final DocRef RERANK_MODEL = new DocRef(OpenAIModelDoc.TYPE, "uuid-2", "rerank");

    @Test
    void testDefaultsForNoSettings() {
        final ElasticDenseVectorFormValues values = ElasticDenseVectorFormValues.fromSettings(null);

        assertThat(values.getEmbeddingModelRef()).isNull();
        assertThat(values.getNearestNeighbourCount()).isZero();
        assertThat(values.getRerankModelRef()).isNull();
        assertThat(values.getRerankBatchSize()).isEqualTo(1000);
        assertThat(values.getRerankTextField()).isNull();
        assertThat(values.getRerankScoreField()).isNull();
        assertThat(values.getRerankScoreMinimum()).isEqualTo(0.8f);
        assertThat(values.isDefault()).isTrue();
        assertThat(values.toSettings(null)).isNull();
    }

    @Test
    void testNewSettingsLeaveNearestNeighbourCountToElasticsearch() {
        final ElasticDenseVectorFormValues values = new ElasticDenseVectorFormValues(EMBEDDING_MODEL,
                0,
                null,
                1000,
                " ",
                "",
                0.8f);

        final ElasticDenseVectorSettings settings = values.toSettings(null);

        assertThat(settings).isNotNull();
        assertThat(settings.getConfig().getEmbeddingModelRef()).isEqualTo(EMBEDDING_MODEL);
        assertThat(settings.getConfig().getNearestNeighbourCount()).isZero();
        assertThat(settings.getRerankTextField()).isNull();
        assertThat(settings.getRerankScoreField()).isNull();
    }

    @Test
    void testRoundTrip() {
        final ElasticDenseVectorFormValues values = new ElasticDenseVectorFormValues(EMBEDDING_MODEL,
                25,
                RERANK_MODEL,
                50,
                " body ",
                "body.score",
                0.5f);

        final ElasticDenseVectorFormValues readBack = ElasticDenseVectorFormValues.fromSettings(
                values.toSettings(null));

        assertThat(readBack.getEmbeddingModelRef()).isEqualTo(EMBEDDING_MODEL);
        assertThat(readBack.getNearestNeighbourCount()).isEqualTo(25);
        assertThat(readBack.getRerankModelRef()).isEqualTo(RERANK_MODEL);
        assertThat(readBack.getRerankBatchSize()).isEqualTo(50);
        assertThat(readBack.getRerankTextField()).isEqualTo("body");
        assertThat(readBack.getRerankScoreField()).isEqualTo("body.score");
        assertThat(readBack.getRerankScoreMinimum()).isEqualTo(0.5f);
        assertThat(readBack.isDefault()).isFalse();
    }

    @Test
    void testSettingsNotOnTheFormAreKept() {
        final DenseVectorFieldConfig existingConfig = DenseVectorFieldConfig.builder()
                .vectorSimilarityFunction(VectorSimilarityFunctionType.COSINE)
                .segmentSize(123)
                .build();
        final ElasticDenseVectorSettings existing = new ElasticDenseVectorSettings(existingConfig, null, null);

        final ElasticDenseVectorSettings settings = new ElasticDenseVectorFormValues(EMBEDDING_MODEL,
                0,
                null,
                1000,
                null,
                null,
                0.8f).toSettings(existing);

        assertThat(settings.getConfig().getVectorSimilarityFunction())
                .isEqualTo(VectorSimilarityFunctionType.COSINE);
        assertThat(settings.getConfig().getSegmentSize()).isEqualTo(123);
        assertThat(settings.getConfig().getNearestNeighbourCount()).isZero();
    }

    @Test
    void testResettingToDefaultsDropsSettings() {
        final ElasticDenseVectorSettings existing = new ElasticDenseVectorSettings(
                DenseVectorFieldConfig.builder().embeddingModelRef(EMBEDDING_MODEL).build(), "body", null);

        final ElasticDenseVectorSettings settings = ElasticDenseVectorFormValues.fromSettings(null)
                .toSettings(existing);

        assertThat(settings).isNull();
    }

    @Test
    void testRerankFieldsWithoutConfig() {
        final ElasticDenseVectorFormValues values = ElasticDenseVectorFormValues.fromSettings(
                new ElasticDenseVectorSettings(null, "body", "score"));

        assertThat(values.getRerankTextField()).isEqualTo("body");
        assertThat(values.getRerankScoreField()).isEqualTo("score");
        assertThat(values.getNearestNeighbourCount()).isZero();
        assertThat(values.isDefault()).isFalse();
    }
}
