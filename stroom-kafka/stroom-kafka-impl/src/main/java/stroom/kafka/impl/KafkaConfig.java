package stroom.kafka.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.cache.CacheConfig;
import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import javax.inject.Singleton;

@Singleton
public class KafkaConfig extends AbstractConfig {

    private CacheConfig kafkaConfigDocCache = new CacheConfig.Builder()
            .maximumSize(1000L)
            .expireAfterAccess(StroomDuration.ofSeconds(10))
            .build();

    @JsonProperty("kafkaConfigDocCache")
    CacheConfig getKafkaConfigDocCache() {
        return kafkaConfigDocCache;
    }

    @SuppressWarnings("unused")
    void setKafkaConfigDocCache(final CacheConfig kafkaConfigDocCache) {
        this.kafkaConfigDocCache = kafkaConfigDocCache;
    }

    @Override
    public String toString() {
        return "KafkaConfig{" +
                "kafkaConfigDocCache=" + kafkaConfigDocCache +
                '}';
    }
}
