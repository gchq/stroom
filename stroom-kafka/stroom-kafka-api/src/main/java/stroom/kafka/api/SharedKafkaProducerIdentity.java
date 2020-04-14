package stroom.kafka.api;

import stroom.kafka.shared.KafkaConfigDoc;

import java.util.Objects;

public class SharedKafkaProducerIdentity {
    private final String configUuid;
    private final String configVersion;

    public SharedKafkaProducerIdentity(final KafkaConfigDoc kafkaConfigDoc) {
        Objects.requireNonNull(kafkaConfigDoc);
        this.configUuid = Objects.requireNonNull(kafkaConfigDoc.getUuid(), "KafkaConfigDoc is missing a UUID");
        this.configVersion = Objects.requireNonNull(kafkaConfigDoc.getVersion(), "KafkaConfigDoc is missing a version");
    }

    public String getConfigUuid() {
        return configUuid;
    }

    public String getConfigVersion() {
        return configVersion;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final SharedKafkaProducerIdentity that = (SharedKafkaProducerIdentity) o;
        return configUuid.equals(that.configUuid) &&
                configVersion.equals(that.configVersion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(configUuid, configVersion);
    }

    @Override
    public String toString() {
        return "SharedKafkaProducerIdentity{" +
                "configUuid='" + configUuid + '\'' +
                ", configVersion='" + configVersion + '\'' +
                '}';
    }
}
