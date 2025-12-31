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

package stroom.index.lucene;

import stroom.index.shared.LuceneIndexField;
import stroom.langchain.api.OpenAIService;
import stroom.openai.shared.OpenAIModelDoc;
import stroom.query.api.datasource.DenseVectorFieldConfig;
import stroom.query.api.datasource.DenseVectorFieldConfig.VectorSimilarityFunctionType;
import stroom.query.api.datasource.IndexField;
import stroom.util.shared.NullSafe;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.lucene.index.VectorSimilarityFunction;

@Singleton
public class DenseVectorFieldCreatorFactory {

    private final OpenAIService openAIService;

    @Inject
    DenseVectorFieldCreatorFactory(final OpenAIService openAIService) {
        this.openAIService = openAIService;
    }

    public DenseVectorFieldCreator create(final IndexField indexField) {
        final LuceneIndexField luceneIndexField = LuceneIndexField.fromIndexField(indexField);
        final DenseVectorFieldConfig denseVectorFieldConfig = indexField.getDenseVectorFieldConfig();

        if (denseVectorFieldConfig == null ||
            denseVectorFieldConfig.getModelRef() == null) {
            throw new IllegalArgumentException("Embedding model is not defined for field " +
                                               indexField);
        }

        // Query the embeddings API for a vector representation of the query expression
        final OpenAIModelDoc modelDoc = openAIService
                .getOpenAIModelDoc(denseVectorFieldConfig.getModelRef());

        final EmbeddingModel embeddingModel = openAIService.getEmbeddingModel(modelDoc);
        final VectorSimilarityFunctionType vectorSimilarityFunctionType =
                NullSafe.getOrElse(luceneIndexField,
                        LuceneIndexField::getDenseVectorFieldConfig,
                        DenseVectorFieldConfig::getVectorSimilarityFunction,
                        VectorSimilarityFunctionType.EUCLIDEAN);
        final VectorSimilarityFunction vectorSimilarityFunction =
                getVectorSimilarityFunction(vectorSimilarityFunctionType);

        return new DenseVectorFieldCreator(
                luceneIndexField,
                denseVectorFieldConfig.getSegmentSize(),
                denseVectorFieldConfig.getOverlapSize(),
                embeddingModel,
                vectorSimilarityFunction);
    }

    private VectorSimilarityFunction getVectorSimilarityFunction(final VectorSimilarityFunctionType type) {
        if (type == null) {
            return VectorSimilarityFunction.EUCLIDEAN;
        }
        return switch (type) {
            case EUCLIDEAN -> VectorSimilarityFunction.EUCLIDEAN;
            case DOT_PRODUCT -> VectorSimilarityFunction.DOT_PRODUCT;
            case COSINE -> VectorSimilarityFunction.COSINE;
            case MAXIMUM_INNER_PRODUCT -> VectorSimilarityFunction.MAXIMUM_INNER_PRODUCT;
        };
    }
}
