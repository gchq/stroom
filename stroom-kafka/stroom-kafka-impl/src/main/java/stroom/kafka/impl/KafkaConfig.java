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

package stroom.kafka.impl;

import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;


@JsonPropertyOrder(alphabetic = true)
public class KafkaConfig extends AbstractConfig implements IsStroomConfig {

    private final String skeletonConfigContent;
    private final CacheConfig kafkaConfigDocCache;

    public KafkaConfig() {
        skeletonConfigContent = DEFAULT_SKELETON_CONFIG_CONTENT;
        kafkaConfigDocCache = CacheConfig.builder()
                .maximumSize(1000L)
                .expireAfterAccess(StroomDuration.ofSeconds(10))
                .build();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public KafkaConfig(@JsonProperty("skeletonConfigContent") final String skeletonConfigContent,
                       @JsonProperty("kafkaConfigDocCache") final CacheConfig kafkaConfigDocCache) {
        this.skeletonConfigContent = skeletonConfigContent;
        this.kafkaConfigDocCache = kafkaConfigDocCache;
    }

    @JsonProperty("skeletonConfigContent")
    @JsonPropertyDescription("The value of this property will be used to pre-populate a new Kafka Configuration. "
            + "It must be in Java Properties File format. Its purpose is to provide a skeleton for creating a working "
            + "Kafka Configuration.")
    public String getSkeletonConfigContent() {
        return skeletonConfigContent;
    }

    @JsonProperty("kafkaConfigDocCache")
    public CacheConfig getKafkaConfigDocCache() {
        return kafkaConfigDocCache;
    }

    @Override
    public String toString() {
        return "KafkaConfig{" +
                "kafkaConfigDocCache=" + kafkaConfigDocCache +
                '}';
    }

    // Put this at the bottom to keep it out of the way
    // See kafkaProducerSkeletonPropsFull.properties and
    // kafkaProducerSkeletonPropsShort.properties
    private static final String DEFAULT_SKELETON_CONFIG_CONTENT = """
            # The following properties are taken from the v2.2 documentation
            # for the Kafka Producer and can be uncommented and set as required.
            # NOTE key.serializer and value.serializer should not be set as
            # these are set within stroom.
            # https://kafka.apache.org/22/documentation.html#producerconfigs

            # The following properties are recommended to be set with values appropriate
            # to your environment.

            # The list of kafka brokers (host:port) to bootstrap the Kafka client with.
            # This can be one or more of the  brokers in the cluster.
            bootstrap.servers=kafka:9092,localhost:9092

            # The ID to use to identify this Kafka producer instance.
            # E.g. 'stroom', 'stroom-statistics', 'stroom-analytics', etc.
            client.id=stroom


            # The following properties are all remaining producer properties that can
            # be set if the Kafka default values are not suitable.
            #acks=
            #buffer.memory=
            #compression.type=
            #retries=
            #ssl.key.password=
            #ssl.keystore.location=
            #ssl.keystore.password=
            #ssl.truststore.location=
            #ssl.truststore.password=
            #batch.size=
            #client.dns.lookup=
            #connections.max.idle.ms=
            #delivery.timeout.ms=
            #linger.ms=
            #max.block.ms=
            #max.request.size=
            #partitioner.class=
            #receive.buffer.bytes=
            #request.timeout.ms=
            #sasl.client.callback.handler.class=
            #sasl.jaas.config=
            #sasl.kerberos.service.name=
            #sasl.login.callback.handler.class=
            #sasl.login.class=
            #sasl.mechanism=
            #security.protocol=
            #send.buffer.bytes=
            #ssl.enabled.protocols=
            #ssl.keystore.type=
            #ssl.protocol=
            #ssl.provider=
            #ssl.truststore.type=
            #enable.idempotence=
            #interceptor.classes=
            #max.in.flight.requests.per.connection=
            #metadata.max.age.ms=
            #metric.reporters=
            #metrics.num.samples=
            #metrics.recording.level=
            #metrics.sample.window.ms=
            #reconnect.backoff.max.ms=
            #reconnect.backoff.ms=
            #retry.backoff.ms=
            #sasl.kerberos.kinit.cmd=
            #sasl.kerberos.min.time.before.relogin=
            #sasl.kerberos.ticket.renew.jitter=
            #sasl.kerberos.ticket.renew.window.factor=
            #sasl.login.refresh.buffer.seconds=
            #sasl.login.refresh.min.period.seconds=
            #sasl.login.refresh.window.factor=
            #sasl.login.refresh.window.jitter=
            #ssl.cipher.suites=
            #ssl.endpoint.identification.algorithm=
            #ssl.keymanager.algorithm=
            #ssl.secure.random.implementation=
            #ssl.trustmanager.algorithm=
            #transaction.timeout.ms=
            #transactional.id=
            """;
}
