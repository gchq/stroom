package stroom.connectors.kafka;

public interface StroomKafkaCallback {

    /**
     * A callback method the user can implement to provide asynchronous handling of request completion. This method will
     * be called when the record sent to the server has been acknowledged. Exactly one of the arguments will be
     * non-null.
     * @param metadata The metadata for the record that was sent (i.e. the partition and offset). Null if an error
     *        occurred.
     * @param exception The exception thrown during processing of this record. Null if no error occurred.
     *                  Possible thrown exceptions include:
     *
     *                  Non-Retriable exceptions (fatal, the message will never be sent):
     *
     *                  InvalidTopicException
     *                  OffsetMetadataTooLargeException
     *                  RecordBatchTooLargeException
     *                  RecordTooLargeException
     *                  UnknownServerException
     *
     *                  Retriable exceptions (transient, may be covered by increasing #.retries):
     *
     *                  CorruptRecordException
     *                  InvalidMetadataException
     *                  NotEnoughReplicasAfterAppendException
     *                  NotEnoughReplicasException
     *                  OffsetOutOfRangeException
     *                  TimeoutException
     *                  UnknownTopicOrPartitionException
     */
    void onCompletion(StroomKafkaRecordMetadata  metadata, Exception exception);
}
