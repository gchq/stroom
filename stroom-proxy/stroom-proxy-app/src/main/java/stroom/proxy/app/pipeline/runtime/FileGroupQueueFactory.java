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

package stroom.proxy.app.pipeline;

import stroom.util.io.PathCreator;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Factory for logical file-group queues.
 */
public class FileGroupQueueFactory {

    private static final String DEFAULT_QUEUE_ROOT = "data/pipeline/queues";

    private final Map<String, QueueDefinition> queueDefinitions;
    private final PathCreator pathCreator;
    private final Map<String, FileGroupQueue> queueCache = new ConcurrentHashMap<>();

    public FileGroupQueueFactory(final ProxyPipelineConfig pipelineConfig,
                                 final PathCreator pathCreator) {
        this(
                Objects.requireNonNull(pipelineConfig, "pipelineConfig").getQueues(),
                pathCreator);
    }

    public FileGroupQueueFactory(final Map<String, QueueDefinition> queueDefinitions,
                                 final PathCreator pathCreator) {
        this.queueDefinitions = Map.copyOf(Objects.requireNonNull(queueDefinitions, "queueDefinitions"));
        this.pathCreator = Objects.requireNonNull(pathCreator, "pathCreator");
    }

    public FileGroupQueue getQueue(final String queueName) {
        final String nonBlankQueueName = requireNonBlank(queueName, "queueName");
        final QueueDefinition definition = queueDefinitions.get(nonBlankQueueName);

        if (definition == null) {
            throw new IllegalArgumentException("No queue definition exists for logical queue "
                                               + nonBlankQueueName + "");
        }

        return queueCache.computeIfAbsent(nonBlankQueueName, ignored -> createQueue(nonBlankQueueName, definition));
    }

    public boolean hasQueue(final String queueName) {
        return queueDefinitions.containsKey(queueName);
    }

    private FileGroupQueue createQueue(final String queueName,
                                       final QueueDefinition definition) {
        return switch (definition.getType()) {
            case LOCAL_FILESYSTEM -> {
                try {
                    yield new LocalFileGroupQueue(
                            queueName,
                            getLocalFilesystemQueuePath(queueName, definition));
                } catch (final IOException e) {
                    throw new UncheckedIOException("Unable to create local filesystem queue " + queueName, e);
                }
            }
            case KAFKA -> new KafkaFileGroupQueue(
                    queueName,
                    definition,
                    new FileGroupQueueMessageCodec());
            case SQS -> new SqsFileGroupQueue(
                    queueName,
                    definition,
                    new FileGroupQueueMessageCodec());
        };
    }

    private Path getLocalFilesystemQueuePath(final String queueName,
                                             final QueueDefinition definition) {
        final String configuredPath = definition.getPath();
        final String path = configuredPath == null
                ? DEFAULT_QUEUE_ROOT + "/" + queueName
                : configuredPath;
        return pathCreator.toAppPath(path);
    }

    private static String requireNonBlank(final String value,
                                          final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}
