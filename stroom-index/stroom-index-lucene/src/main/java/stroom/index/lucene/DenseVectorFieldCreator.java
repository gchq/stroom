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
import stroom.langchain.api.SimpleTokenCountEstimator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.index.VectorSimilarityFunction;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class DenseVectorFieldCreator {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(DenseVectorFieldCreator.class);

    private final LuceneIndexField luceneIndexField;
    private final int segmentSize;
    private final int overlapSize;
    private final EmbeddingModel embeddingModel;
    private final VectorSimilarityFunction vectorSimilarityFunction;
    private final SimpleTokenCountEstimator estimator = new SimpleTokenCountEstimator();

    public DenseVectorFieldCreator(final LuceneIndexField luceneIndexField,
                                   final int segmentSize,
                                   final int overlapSize,
                                   final EmbeddingModel embeddingModel,
                                   final VectorSimilarityFunction vectorSimilarityFunction) {
        this.luceneIndexField = luceneIndexField;
        this.segmentSize = segmentSize;
        this.overlapSize = overlapSize;
        this.embeddingModel = embeddingModel;
        this.vectorSimilarityFunction = vectorSimilarityFunction;
    }

    public Collection<Field> getFields(final String value) {
        try {
            LOGGER.trace("getFields for {}", value);
            final List<TextSegment> segments = DocumentSplitters
                    .recursive(
                            segmentSize,
                            overlapSize,
                            estimator)
                    .split(Document.from(value));
            LOGGER.debug("About to get vectors for {} segments", segments.size());
            return segments.stream().map(segment -> {
                try {
                    LOGGER.trace("Getting vectors for {}", segment.text());
                    final float[] vectors = embeddingModel.embed(segment).content().vector();
                    LOGGER.trace("Got {} vectors for {}", vectors, segment.text());
                    // Create KNN Float Vector Field.
                    return (Field) new KnnFloatVectorField(
                            luceneIndexField.getFldName(),
                            vectors,
                            vectorSimilarityFunction);
                } catch (final Exception e) {
                    // For now we are just going to log errors to the logger rather than disturbing the indexing
                    // process.
                    LOGGER.error(e::getMessage, e);
                    throw new RuntimeException(e.getMessage(), e);
                }
            }).toList();
        } catch (final Exception e) {
            // For now we are just going to log errors to the logger rather than disturbing the indexing process.
            LOGGER.error(e::getMessage, e);
        }
        return Collections.emptyList();
    }
}
