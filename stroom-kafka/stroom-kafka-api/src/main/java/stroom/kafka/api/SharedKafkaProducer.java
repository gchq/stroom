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

package stroom.kafka.api;

import org.apache.kafka.clients.producer.KafkaProducer;

import java.util.Optional;

/**
 * A wrapper for a shared {@link KafkaProducer} that MUST be used in a try-with-resources
 * block (or similar mechanism to call {@link SharedKafkaProducer#close()}) so that
 * the {@link KafkaProducer} can be closed when no longer needed by all parties.
 * An instance may not contain a {@link KafkaProducer}, e.g. when no {@link stroom.kafka.shared.KafkaConfigDoc}
 * can be found for a UUID.
 * <p>
 * Users of this class should NOT call close() on the KafkaProducer themselves as it is potentially shared.
 * They are permitted to flush it though.
 */
public interface SharedKafkaProducer extends AutoCloseable {

    Optional<KafkaProducer<String, byte[]>> getKafkaProducer();

    boolean hasKafkaProducer();

    String getConfigName();

    String getConfigUuid();

    String getConfigVersion();

    /**
     * This does not close the contained {@link KafkaProducer}, it just marks it
     * as being no longer in use by the caller. The {@link KafkaProducerFactory}
     * is responsible for closing the {@link KafkaProducer}
     */
    @Override
    void close();
}
