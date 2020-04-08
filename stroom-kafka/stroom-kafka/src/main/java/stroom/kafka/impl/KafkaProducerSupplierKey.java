package stroom.kafka.impl;

import stroom.kafkaConfig.shared.KafkaConfigDoc;

import java.util.Objects;

class KafkaProducerSupplierKey {
    private final String uuid;
    private final String version;

    KafkaProducerSupplierKey(final KafkaConfigDoc kafkaConfigDoc) {
        Objects.requireNonNull(kafkaConfigDoc);
        this.uuid = Objects.requireNonNull(kafkaConfigDoc.getUuid());
        this.version = Objects.requireNonNull(kafkaConfigDoc.getVersion());
    }

    KafkaProducerSupplierKey(final String uuid, final String version) {
        this.uuid = Objects.requireNonNull(uuid);
        this.version = Objects.requireNonNull(version);
    }

    String getUuid() {
        return uuid;
    }

    String getVersion() {
        return version;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final KafkaProducerSupplierKey that = (KafkaProducerSupplierKey) o;
        return uuid.equals(that.uuid) &&
                version.equals(that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uuid, version);
    }

    @Override
    public String toString() {
        return "KafkaProducerSupplierKey{" +
                "uuid='" + uuid + '\'' +
                ", version='" + version + '\'' +
                '}';
    }
}
