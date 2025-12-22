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

import stroom.docref.DocRef;

public interface KafkaProducerFactory {

    /**
     * Gets or creates a shared KafkaProducer for the supplied config docref. The
     * {@link org.apache.kafka.clients.producer.KafkaProducer} will be wrapped in a
     * {@link SharedKafkaProducer}. If no config can be found an empty {@link SharedKafkaProducer}
     * will be returned.
     * <p>
     * The {@link SharedKafkaProducer} should either be used with a try-with-resources block
     * or  {@link KafkaProducerFactory#returnSharedKafkaProducer} should be called when it is finished with.
     */
    SharedKafkaProducer getSharedProducer(final DocRef kafkaConfigDocRef);

    /**
     * 'Returns' the {@link SharedKafkaProducer} to the factory so it can close the wrapped producer
     * if required. This is for use when a try-with-resources block is not applicable.
     * Should only be called once and the {@link SharedKafkaProducer} should not be
     * used after it is called.
     */
    void returnSharedKafkaProducer(final SharedKafkaProducer sharedKafkaProducer);
}
