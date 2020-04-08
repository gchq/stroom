package stroom.kafka.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import javax.inject.Singleton;

@Singleton
public class KafkaConfig extends AbstractConfig {

    private String skeletonConfigContent = DEFAULT_SKELETON_CONFIG_CONTENT;

    private CacheConfig kafkaConfigDocCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofSeconds(10))
            .build();

    @JsonProperty("skeletonConfigContent")
    public String getSkeletonConfigContent() {
        return skeletonConfigContent;
    }

    @SuppressWarnings("unused")
    void setSkeletonConfigContent(final String skeletonConfigContent) {
        this.skeletonConfigContent = skeletonConfigContent;
    }

    @JsonProperty("kafkaConfigDocCache")
    public CacheConfig getKafkaConfigDocCache() {
        return kafkaConfigDocCache;
    }

    @SuppressWarnings("unused")
    public void setKafkaConfigDocCache(final CacheConfig kafkaConfigDocCache) {
        this.kafkaConfigDocCache = kafkaConfigDocCache;
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
    private static final String DEFAULT_SKELETON_CONFIG_CONTENT = ""
            + "# The following properties are taken from the v2.2 documentation\n"
            + "# for the Kafka Producer and can be uncommented and set as required.\n"
            + "# NOTE key.serializer and value.serializer should not be set as\n"
            + "# these are set within stroom.\n"
            + "# https://kafka.apache.org/22/documentation.html#producerconfigs\n"
            + "\n"
            + "#acks=\n"
            + "#bootstrap.servers=\n"
            + "#buffer.memory=\n"
            + "#compression.type=\n"
            + "#retries=\n"
            + "#ssl.key.password=\n"
            + "#ssl.keystore.location=\n"
            + "#ssl.keystore.password=\n"
            + "#ssl.truststore.location=\n"
            + "#ssl.truststore.password=\n"
            + "#batch.size=\n"
            + "#client.dns.lookup=\n"
            + "#client.id=\n"
            + "#connections.max.idle.ms=\n"
            + "#delivery.timeout.ms=\n"
            + "#linger.ms=\n"
            + "#max.block.ms=\n"
            + "#max.request.size=\n"
            + "#partitioner.class=\n"
            + "#receive.buffer.bytes=\n"
            + "#request.timeout.ms=\n"
            + "#sasl.client.callback.handler.class=\n"
            + "#sasl.jaas.config=\n"
            + "#sasl.kerberos.service.name=\n"
            + "#sasl.login.callback.handler.class=\n"
            + "#sasl.login.class=\n"
            + "#sasl.mechanism=\n"
            + "#security.protocol=\n"
            + "#send.buffer.bytes=\n"
            + "#ssl.enabled.protocols=\n"
            + "#ssl.keystore.type=\n"
            + "#ssl.protocol=\n"
            + "#ssl.provider=\n"
            + "#ssl.truststore.type=\n"
            + "#enable.idempotence=\n"
            + "#interceptor.classes=\n"
            + "#max.in.flight.requests.per.connection=\n"
            + "#metadata.max.age.ms=\n"
            + "#metric.reporters=\n"
            + "#metrics.num.samples=\n"
            + "#metrics.recording.level=\n"
            + "#metrics.sample.window.ms=\n"
            + "#reconnect.backoff.max.ms=\n"
            + "#reconnect.backoff.ms=\n"
            + "#retry.backoff.ms=\n"
            + "#sasl.kerberos.kinit.cmd=\n"
            + "#sasl.kerberos.min.time.before.relogin=\n"
            + "#sasl.kerberos.ticket.renew.jitter=\n"
            + "#sasl.kerberos.ticket.renew.window.factor=\n"
            + "#sasl.login.refresh.buffer.seconds=\n"
            + "#sasl.login.refresh.min.period.seconds=\n"
            + "#sasl.login.refresh.window.factor=\n"
            + "#sasl.login.refresh.window.jitter=\n"
            + "#ssl.cipher.suites=\n"
            + "#ssl.endpoint.identification.algorithm=\n"
            + "#ssl.keymanager.algorithm=\n"
            + "#ssl.secure.random.implementation=\n"
            + "#ssl.trustmanager.algorithm=\n"
            + "#transaction.timeout.ms=\n"
            + "#transactional.id=\n";

}
