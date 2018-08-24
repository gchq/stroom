package stroom.kafka.pipeline;

/**
 * This is a shim of the ProducerRecord from all versions of the Kafka client libraries.
 *
 * We are abstracting away those client libraries so that we can load in hard implementations at runtime without
 * polluting the classpath.
 *
 * @param <K> The Key type
 * @param <V> The Value type
 */
public class KafkaProducerRecord<K, V> {
    private final String topic;
    private final Integer partition;
    private final K key;
    private final V value;
    private final Long timestamp;

    /**
     * Creates a record with a specified timestamp to be sent to a specified topic and partition
     *
     * @param topic The topic the record will be appended to
     * @param partition The partition to which the record should be sent
     * @param timestamp The timestamp of the record
     * @param key The key that will be included in the record
     * @param value The record contents
     */
    private KafkaProducerRecord(String topic, Integer partition, Long timestamp, K key, V value) {
        if (topic == null)
            throw new IllegalArgumentException("Topic cannot be null");
        if (timestamp != null && timestamp < 0)
            throw new IllegalArgumentException("Invalid timestamp " + timestamp);
        this.topic = topic;
        this.partition = partition;
        this.key = key;
        this.value = value;
        this.timestamp = timestamp;
    }

    /**
     * @return The topic this record is being sent to
     */
    public String topic() {
        return topic;
    }

    /**
     * @return The key (or null if no key is specified)
     */
    public K key() {
        return key;
    }

    /**
     * @return The value
     */
    public V value() {
        return value;
    }

    /**
     * @return The timestamp
     */
    public Long timestamp() {
        return timestamp;
    }

    /**
     * @return The partition to which the record will be sent (or null if no partition was specified)
     */
    public Integer partition() {
        return partition;
    }

    @Override
    public String toString() {
        String key = this.key == null ? "null" : this.key.toString();
        String value = this.value == null ? "null" : this.value.toString();
        String timestamp = this.timestamp == null ? "null" : this.timestamp.toString();
        return "ProducerRecord(topic=" + topic + ", partition=" + partition + ", key=" + key + ", value=" + value +
                ", timestamp=" + timestamp + ")";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        else if (!(o instanceof KafkaProducerRecord))
            return false;

        KafkaProducerRecord<?, ?> that = (KafkaProducerRecord<?, ?>) o;

        if (key != null ? !key.equals(that.key) : that.key != null)
            return false;
        else if (partition != null ? !partition.equals(that.partition) : that.partition != null)
            return false;
        else if (topic != null ? !topic.equals(that.topic) : that.topic != null)
            return false;
        else if (value != null ? !value.equals(that.value) : that.value != null)
            return false;
        else if (timestamp != null ? !timestamp.equals(that.timestamp) : that.timestamp != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = topic != null ? topic.hashCode() : 0;
        result = 31 * result + (partition != null ? partition.hashCode() : 0);
        result = 31 * result + (key != null ? key.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (timestamp != null ? timestamp.hashCode() : 0);
        return result;
    }

    public static class Builder<K, V> {
        private String topic;
        private Integer partition;
        private K key;
        private V value;
        private Long timestamp;

        public Builder<K, V> topic(final String topic) {
            this.topic = topic;
            return this;
        }

        public Builder<K, V> partition(final Integer partition) {
            this.partition = partition;
            return this;
        }

        public Builder<K, V> key(final K key) {
            this.key = key;
            return this;
        }

        public Builder<K, V> value(final V value) {
            this.value = value;
            return this;
        }

        public Builder<K, V> timestamp(final Long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public KafkaProducerRecord<K, V> build() {
            return new KafkaProducerRecord<>(topic, partition, timestamp, key, value);
        }
    }
}
