package stroom.kafka.api;

public interface StroomKafkaRecordMetaData {

    long getOffset();

    long getTimestamp();

    int getPartition();

    String getTopic();

//    private final long offset;
//    private final long timestamp;
//    private final int partition;
//    private final String topic;
//
//    public StroomKafkaRecordMetaData(final long offset,
//                                     final long timestamp,
//                                     final int partition,
//                                     final String topic) {
//        this.offset = offset;
//        this.timestamp = timestamp;
//        this.partition = partition;
//        this.topic = topic;
//    }
//
//    public long getOffset() {
//        return offset;
//    }
//
//    public long getTimestamp() {
//        return timestamp;
//    }
//
//    public int getPartition() {
//        return partition;
//    }
//
//    public String getTopic() {
//        return topic;
//    }
}
