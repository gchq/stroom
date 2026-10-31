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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.MergedPipeline;
import stroom.ai.impl.tools.MergedPipeline.ElementProperty;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/// What processing a stream with a pipeline writes, read from its elements and properties as Stroom runs it: an output
/// stream (and of which type), Kafka messages, or an index, for the processing write tools to share.
class PipelineOutputs {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PipelineOutputs.class);

    /// The element types that index what they are given.
    static final Set<String> INDEXING_ELEMENTS = Set.of("IndexingFilter", "ElasticIndexingFilter");
    /// The stream type a pipeline is taken to write when its stream appender doesn't say.
    static final String EVENTS = "Events";

    private final PipelineShapes pipelineShapes;

    /// @param pipelineShapes Reads a pipeline as Stroom runs it.
    @Inject
    PipelineOutputs(final PipelineShapes pipelineShapes) {
        this.pipelineShapes = pipelineShapes;
    }

    /// @param pipelineUuid A pipeline.
    /// @return The stream type it writes, from its stream appender (Events, Reference, Records, ...); Events when
    /// it doesn't say, or the pipeline can't be read.
    String outputStreamType(final String pipelineUuid) {
        final MergedPipeline merged;
        try {
            merged = pipelineShapes.merged(pipelineUuid);
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to read the output stream type of {}", pipelineUuid, e);
            return EVENTS;
        }
        final Map<String, String> types = merged.elements();
        for (final Map.Entry<ElementProperty, Object> entry : merged.properties().entrySet()) {
            final ElementProperty property = entry.getKey();
            if ("StreamAppender".equals(types.get(property.element()))
                && "streamType".equals(property.name())
                && entry.getValue() != null
                && !String.valueOf(entry.getValue()).isEmpty()) {
                return String.valueOf(entry.getValue());
            }
        }
        return EVENTS;
    }

    /// Whether processing a stream gives an output stream: a StreamAppender, and no indexing. An indexing
    /// pipeline, or one that sends elsewhere (Kafka, say), writes only an Error stream, and only when something
    /// went wrong.
    ///
    /// @param pipelineUuid A pipeline.
    /// @return True if it writes an output stream, or it can't be read.
    boolean writesOutput(final String pipelineUuid) {
        final Collection<String> types;
        try {
            types = pipelineShapes.merged(pipelineUuid).elements().values();
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to read whether {} writes an output stream", pipelineUuid, e);
            return true;
        }
        return types.contains("StreamAppender") && types.stream().noneMatch(INDEXING_ELEMENTS::contains);
    }

    /// @param pipelineUuid A pipeline.
    /// @return True if it sends to Kafka; false if it doesn't, or it can't be read.
    boolean sendsToKafka(final String pipelineUuid) {
        try {
            return pipelineShapes.merged(pipelineUuid).elements().containsValue("StandardKafkaProducer");
        } catch (final RuntimeException e) {
            LOGGER.debug("Unable to read whether {} sends to Kafka", pipelineUuid, e);
            return false;
        }
    }

    /// @param pipelineUuid A pipeline.
    /// @return True if it indexes (Lucene or Elasticsearch).
    /// @throws PipelineUnreadableException if Stroom can't load the pipeline.
    boolean isIndexing(final String pipelineUuid) {
        return pipelineShapes.merged(pipelineUuid).elements().values().stream()
                .anyMatch(INDEXING_ELEMENTS::contains);
    }
}
