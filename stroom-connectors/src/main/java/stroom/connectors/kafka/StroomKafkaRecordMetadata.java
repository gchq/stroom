package stroom.connectors.kafka;

public class StroomKafkaRecordMetadata {

    private final long offset;
    // The timestamp of the message.
    // If LogAppendTime is used for the topic, the timestamp will be the timestamp returned by the broker.
    // If CreateTime is used for the topic, the timestamp is the timestamp in the corresponding ProducerRecord if the
    // user provided one. Otherwise, it will be the producer local time when the producer record was handed to the
    // producer.
    private final long timestamp;
    private final long checksum;
    private final int serializedKeySize;
    private final int serializedValueSize;
    private final String topic;
    private final int partition;

    public StroomKafkaRecordMetadata(final String topic,
                                      final int partition,
                                      final long offset,
                                      final long timestamp,
                                      final long checksum,
                                      final int serializedKeySize,
                                      final int serializedValueSize) {
        super();
        this.offset = offset;
        this.timestamp = timestamp;
        this.checksum = checksum;
        this.serializedKeySize = serializedKeySize;
        this.serializedValueSize = serializedValueSize;
        this.topic = topic;
        this.partition = partition;
    }

    /**
     * The offset of the record in the topic/partition.
     */
    public long offset() {
        return this.offset;
    }

    /**
     * The timestamp of the record in the topic/partition.
     */
    public long timestamp() {
        return timestamp;
    }

    /**
     * The checksum (CRC32) of the record.
     */
    public long checksum() {
        return this.checksum;
    }

    /**
     * The size of the serialized, uncompressed key in bytes. If key is null, the returned size
     * is -1.
     */
    public int serializedKeySize() {
        return this.serializedKeySize;
    }

    /**
     * The size of the serialized, uncompressed value in bytes. If value is null, the returned
     * size is -1.
     */
    public int serializedValueSize() {
        return this.serializedValueSize;
    }

    /**
     * The topic the record was appended to
     */
    public String topic() {
        return this.topic;
    }

    /**
     * The partition the record was sent to
     */
    public int partition() {
        return this.partition;
    }

    @Override
    public String toString() {
        return topic + "-" + partition + "@" + offset;
    }
}
