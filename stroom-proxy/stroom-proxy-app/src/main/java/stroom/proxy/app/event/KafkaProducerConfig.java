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

package stroom.proxy.app.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Collections;
import java.util.List;

@JsonPropertyOrder(alphabetic = true)
public class KafkaProducerConfig {

    private final List<String> bootstrapServers;
    private final String clientDnsLookup;
    private final Long metadataMaxAgeMs;
    private final Integer batchSize;
    private final String acks;
    private final Long lingerMs;
    private final Integer requestTimeoutMs;
    private final Long deliveryTimeoutMs;
    private final String clientId;
    private final Integer sendBufferBytes;
    private final Integer receiveBufferBytes;
    private final Integer maxRequestSize;
    private final Long reconnectBackoffMs;
    private final Long reconnectBackoffMaxMs;
    private final Long maxBlockMs;
    private final Long bufferMemory;
    private final Long retryBackoffMs;
    private final String compressionType;
    private final Long metricsSampleWindowMs;
    private final Integer metricsNumSamples;
    private final String metricsRecordingLevel;
    private final List<String> metricReporters;
    private final Integer maxInFlightRequestsPerConnection;
    private final Integer retries;
    private final String keySerializer;
    private final String valueSerializer;
    private final Long connectionsMaxIdleMs;
    private final String partitionerClass;
    private final List<String> interceptorClasses;
    private final Boolean enableIdempotence;
    private final Integer transactionTimeoutMs;
    private final String transactionalId;

    public KafkaProducerConfig() {
        this.bootstrapServers = Collections.emptyList();
        this.clientDnsLookup = null;
        this.metadataMaxAgeMs = 300000L;
        this.batchSize = 16384;
        this.acks = null;
        this.lingerMs = 0L;
        this.requestTimeoutMs = 30000;
        this.deliveryTimeoutMs = 120000L;
        this.clientId = null;
        this.sendBufferBytes = 131072;
        this.receiveBufferBytes = 32768;
        this.maxRequestSize = 1048576;
        this.reconnectBackoffMs = 50L;
        this.reconnectBackoffMaxMs = 1000L;
        this.maxBlockMs = 60000L;
        this.bufferMemory = 33554432L;
        this.retryBackoffMs = 100L;
        this.compressionType = "none";
        this.metricsSampleWindowMs = 30000L;
        this.metricsNumSamples = 2;
        this.metricsRecordingLevel = "INFO";
        this.metricReporters = Collections.emptyList();
        this.maxInFlightRequestsPerConnection = 5;
        this.retries = 2147483647;
        this.keySerializer = null;
        this.valueSerializer = null;
        this.connectionsMaxIdleMs = 540000L;
        this.partitionerClass = null;
        this.interceptorClasses = Collections.emptyList();
        this.enableIdempotence = true;
        this.transactionTimeoutMs = 60000;
        this.transactionalId = null;
    }

    @JsonCreator
    public KafkaProducerConfig(@JsonProperty("bootstrapServers") final List<String> bootstrapServers,
                               @JsonProperty("clientDnsLookup") final String clientDnsLookup,
                               @JsonProperty("metadataMaxAgeMs") final Long metadataMaxAgeMs,
                               @JsonProperty("batchSize") final Integer batchSize,
                               @JsonProperty("acks") final String acks,
                               @JsonProperty("lingerMs") final Long lingerMs,
                               @JsonProperty("requestTimeoutMs") final Integer requestTimeoutMs,
                               @JsonProperty("deliveryTimeoutMs") final Long deliveryTimeoutMs,
                               @JsonProperty("clientId") final String clientId,
                               @JsonProperty("sendBufferBytes") final Integer sendBufferBytes,
                               @JsonProperty("receiveBufferBytes") final Integer receiveBufferBytes,
                               @JsonProperty("maxRequestSize") final Integer maxRequestSize,
                               @JsonProperty("reconnectBackoffMs") final Long reconnectBackoffMs,
                               @JsonProperty("reconnectBackoffMaxMs") final Long reconnectBackoffMaxMs,
                               @JsonProperty("maxBlockMs") final Long maxBlockMs,
                               @JsonProperty("bufferMemory") final Long bufferMemory,
                               @JsonProperty("retryBackoffMs") final Long retryBackoffMs,
                               @JsonProperty("compressionType") final String compressionType,
                               @JsonProperty("metricsSampleWindowMs") final Long metricsSampleWindowMs,
                               @JsonProperty("metricsNumSamples") final Integer metricsNumSamples,
                               @JsonProperty("metricsRecordingLevel") final String metricsRecordingLevel,
                               @JsonProperty("metricReporters") final List<String> metricReporters,
                               @JsonProperty("maxInFlightRequestsPerConnection") final Integer
                                       maxInFlightRequestsPerConnection,
                               @JsonProperty("retries") final Integer retries,
                               @JsonProperty("keySerializer") final String keySerializer,
                               @JsonProperty("valueSerializer") final String valueSerializer,
                               @JsonProperty("connectionsMaxIdleMs") final Long connectionsMaxIdleMs,
                               @JsonProperty("partitionerClass") final String partitionerClass,
                               @JsonProperty("interceptorClasses") final List<String> interceptorClasses,
                               @JsonProperty("enableIdempotence") final Boolean enableIdempotence,
                               @JsonProperty("transactionTimeoutMs") final Integer transactionTimeoutMs,
                               @JsonProperty("transactionalId") final String transactionalId) {
        this.bootstrapServers = bootstrapServers;
        this.clientDnsLookup = clientDnsLookup;
        this.metadataMaxAgeMs = metadataMaxAgeMs;
        this.batchSize = batchSize;
        this.acks = acks;
        this.lingerMs = lingerMs;
        this.requestTimeoutMs = requestTimeoutMs;
        this.deliveryTimeoutMs = deliveryTimeoutMs;
        this.clientId = clientId;
        this.sendBufferBytes = sendBufferBytes;
        this.receiveBufferBytes = receiveBufferBytes;
        this.maxRequestSize = maxRequestSize;
        this.reconnectBackoffMs = reconnectBackoffMs;
        this.reconnectBackoffMaxMs = reconnectBackoffMaxMs;
        this.maxBlockMs = maxBlockMs;
        this.bufferMemory = bufferMemory;
        this.retryBackoffMs = retryBackoffMs;
        this.compressionType = compressionType;
        this.metricsSampleWindowMs = metricsSampleWindowMs;
        this.metricsNumSamples = metricsNumSamples;
        this.metricsRecordingLevel = metricsRecordingLevel;
        this.metricReporters = metricReporters;
        this.maxInFlightRequestsPerConnection = maxInFlightRequestsPerConnection;
        this.retries = retries;
        this.keySerializer = keySerializer;
        this.valueSerializer = valueSerializer;
        this.connectionsMaxIdleMs = connectionsMaxIdleMs;
        this.partitionerClass = partitionerClass;
        this.interceptorClasses = interceptorClasses;
        this.enableIdempotence = enableIdempotence;
        this.transactionTimeoutMs = transactionTimeoutMs;
        this.transactionalId = transactionalId;
    }

    @JsonProperty
    @JsonPropertyDescription("A list of host/port pairs to use for establishing the initial connection to the Kafka " +
            "cluster. The client will make use of all servers irrespective of which servers are specified here for " +
            "bootstrapping&mdash;this list only impacts the initial hosts used to discover the full set of servers. " +
            "This list should be in the form <code>host1:port1,host2:port2,...</code>. Since these servers are just " +
            "used for the initial connection to discover the full cluster membership (which may change dynamically), " +
            "this list need not contain the full set of servers (you may want more than one, though, in case a " +
            "server is down).")
    public List<String> getBootstrapServers() {
        return bootstrapServers;
    }

    @JsonPropertyDescription("Controls how the client uses DNS lookups. If set to " +
            "<code>use_all_dns_ips</code> then, when the lookup returns multiple IP addresses for a hostname, they " +
            "will all be attempted to connect to before failing the connection. Applies to both bootstrap and " +
            "advertised servers. If the value is <code>resolve_canonical_bootstrap_servers_only</code> each entry " +
            "will be resolved and expanded into a list of canonical names. ")
    @JsonProperty
    public String getClientDnsLookup() {
        return clientDnsLookup;
    }

    @JsonPropertyDescription("The period of time in milliseconds after which we force a refresh of metadata even if " +
            "we haven't seen any partition leadership changes to proactively discover any new brokers or partitions.")
    @JsonProperty
    public Long getMetadataMaxAgeMs() {
        return metadataMaxAgeMs;
    }

    @JsonPropertyDescription("The producer will attempt to batch records together into fewer requests whenever " +
            "multiple records are being sent to the same partition. This helps performance on both the client and " +
            "the server. This configuration controls the default batch size in bytes. No attempt will be made to " +
            "batch records larger than this size. Requests sent to brokers will contain multiple batches, one for " +
            "each partition with data available to be sent. A small batch size will make batching less common and " +
            "may reduce throughput (a batch size of zero will disable batching entirely). A very large batch size " +
            "may use memory a bit more wastefully as we will always allocate a buffer of the specified batch size " +
            "in anticipation of additional records.")
    @JsonProperty
    public Integer getBatchSize() {
        return batchSize;
    }

    @JsonPropertyDescription("The number of acknowledgments the producer requires the leader to have received before " +
            "considering a request complete. This controls the durability of records that are sent. The following " +
            "settings are allowed: <ul>" +
            " <li><code>acks=0</code> If set to zero then the producer will not wait for any acknowledgment from the" +
            " server at all. The record will be immediately added to the socket buffer and considered sent. No " +
            "guarantee can be" +
            " made that the server has received the record in this case, and the <code>retries</code> configuration " +
            "will not" +
            " take effect (as the client won't generally know of any failures). The offset given back for each " +
            "record will" +
            " always be set to -1." +
            " <li><code>acks=1</code> This will mean the leader will write the record to its local log but will " +
            "respond" +
            " without awaiting full acknowledgement from all followers. In this case should the leader fail " +
            "immediately after" +
            " acknowledging the record but before the followers have replicated it then the record will be lost." +
            " <li><code>acks=all</code> This means the leader will wait for the full set of in-sync replicas to" +
            " acknowledge the record. This guarantees that the record will not be lost as long as at least one " +
            "in-sync replica" +
            " remains alive. This is the strongest available guarantee. This is equivalent to the acks=-1 setting.")
    @JsonProperty
    public String getAcks() {
        return acks;
    }

    @JsonPropertyDescription("The producer groups together any records that arrive in between request transmissions " +
            "into a single batched request. "
            + "Normally this occurs only under load when records arrive faster than they can be sent out. However in " +
            "some circumstances the client may want to "
            + "reduce the number of requests even under moderate load. This setting accomplishes this by adding a " +
            "small amount "
            + "of artificial delay&mdash;that is, rather than immediately sending out a record the producer will " +
            "wait for up to "
            + "the given delay to allow other records to be sent so that the sends can be batched together. This " +
            "can be thought "
            + "of as analogous to Nagle's algorithm in TCP. This setting gives the upper bound on the delay for " +
            "batching: once "
            + "we get <code>batchSize</code> worth of records for a partition it will be sent immediately " +
            "regardless of this "
            + "setting, however if we have fewer than this many bytes accumulated for this partition we will " +
            "'linger' for the "
            + "specified time waiting for more records to show up. This setting defaults to 0 (i.e. no delay). " +
            "Setting <code>lingerMs</code>, "
            + "for example, would have the effect of reducing the number of requests sent but would add up to 5ms " +
            "of latency to records sent in the absence of load.")
    @JsonProperty
    public Long getLingerMs() {
        return lingerMs;
    }

    @JsonPropertyDescription("The configuration controls the maximum amount of time the client will wait "
            + "for the response of a request. If the response is not received before the timeout "
            + "elapses the client will resend the request if necessary or fail the request if "
            + "retries are exhausted."

            + " This should be larger than <code>replica.lag.time.max.ms</code> (a broker configuration)"
            + " to reduce the possibility of message duplication due to unnecessary producer retries.")
    @JsonProperty
    public Integer getRequestTimeoutMs() {
        return requestTimeoutMs;
    }

    @JsonPropertyDescription("An upper bound on the time to report success or failure "
            + "after a call to <code>send()</code> returns. This limits the total time that a record will be delayed "
            + "prior to sending, the time to await acknowledgement from the broker (if expected), and the time " +
            "allowed "
            + "for retriable send failures. The producer may report failure to send a record earlier than this " +
            "config if "
            + "either an unrecoverable error is encountered, the retries have been exhausted, "
            + "or the record is added to a batch which reached an earlier delivery expiration deadline. "
            + "The value of this config should be greater than or equal to the sum of <code>requestTimeoutMs</code> "
            + "and <code>lingerMs</code>.")
    @JsonProperty
    public Long getDeliveryTimeoutMs() {
        return deliveryTimeoutMs;
    }

    @JsonPropertyDescription("An id string to pass to the server when making requests. The purpose of this is to be " +
            "able to track the source of requests beyond just ip/port by allowing a logical application name to be " +
            "included in server-side request logging.")
    @JsonProperty
    public String getClientId() {
        return clientId;
    }

    @JsonPropertyDescription("The size of the TCP send buffer (SO_SNDBUF) to use when sending data. If the value " +
            "is -1, the OS default will be used.")
    @JsonProperty
    public Integer getSendBufferBytes() {
        return sendBufferBytes;
    }

    @JsonPropertyDescription("The size of the TCP receive buffer (SO_RCVBUF) to use when reading data. If the value " +
            "is -1, the OS default will be used.")
    @JsonProperty
    public Integer getReceiveBufferBytes() {
        return receiveBufferBytes;
    }

    @JsonPropertyDescription("The maximum size of a request in bytes. This setting will limit the number of record "
            + "batches the producer will send in a single request to avoid sending huge requests. "
            + "This is also effectively a cap on the maximum record batch size. Note that the server "
            + "has its own cap on record batch size which may be different from this.")
    @JsonProperty
    public Integer getMaxRequestSize() {
        return maxRequestSize;
    }

    @JsonPropertyDescription("The base amount of time to wait before attempting to reconnect to a given host. This " +
            "avoids repeatedly connecting to a host in a tight loop. This backoff applies to all connection attempts " +
            "by the client to a broker.")
    @JsonProperty
    public Long getReconnectBackoffMs() {
        return reconnectBackoffMs;
    }

    @JsonPropertyDescription("The maximum amount of time in milliseconds to wait when reconnecting to a broker that " +
            "has repeatedly failed to connect. If provided, the backoff per host will increase exponentially for " +
            "each consecutive connection failure, up to this maximum. After calculating the backoff increase, 20% " +
            "random jitter is added to avoid connection storms.")
    @JsonProperty
    public Long getReconnectBackoffMaxMs() {
        return reconnectBackoffMaxMs;
    }

    @JsonPropertyDescription("The configuration controls how long <code>KafkaProducer.send()</code> and " +
            "<code>KafkaProducer.partitionsFor()</code> will block."
            + "These methods can be blocked either because the buffer is full or metadata unavailable."
            + "Blocking in the user-supplied serializers or partitioner will not be counted against this timeout.")
    @JsonProperty
    public Long getMaxBlockMs() {
        return maxBlockMs;
    }

    @JsonPropertyDescription("The total bytes of memory the producer can use to buffer records waiting to be sent " +
            "to the server. If records are "
            + "sent faster than they can be delivered to the server the producer will block for" +
            " <code>maxBlockMs</code> after which it will throw an exception."
            + " "
            + "This setting should correspond roughly to the total memory the producer will use, but is not a " +
            "hard bound since "
            + "not all memory the producer uses is used for buffering. Some additional memory will be used for " +
            "compression (if "
            + "compression is enabled) as well as for maintaining in-flight requests.")
    @JsonProperty
    public Long getBufferMemory() {
        return bufferMemory;
    }

    @JsonPropertyDescription("The amount of time to wait before attempting to retry a failed request to a given " +
            "topic partition. This avoids repeatedly sending requests in a tight loop under some failure scenarios.")
    @JsonProperty
    public Long getRetryBackoffMs() {
        return retryBackoffMs;
    }

    @JsonPropertyDescription("The compression type for all data generated by the producer. The default is none " +
            "(i.e. no compression). Valid "
            + " values are <code>none</code>, <code>gzip</code>, <code>snappy</code>, <code>lz4</code>, or " +
            "<code>zstd</code>. "
            + "Compression is of full batches of data, so the efficacy of batching will also impact the " +
            "compression ratio (more batching means better compression).")
    @JsonProperty
    public String getCompressionType() {
        return compressionType;
    }

    @JsonPropertyDescription("The window of time a metrics sample is computed over.")
    @JsonProperty
    public Long getMetricsSampleWindowMs() {
        return metricsSampleWindowMs;
    }

    @JsonPropertyDescription("The number of samples maintained to compute metrics.")
    @JsonProperty
    public Integer getMetricsNumSamples() {
        return metricsNumSamples;
    }

    @JsonPropertyDescription("The highest recording level for metrics.")
    @JsonProperty
    public String getMetricsRecordingLevel() {
        return metricsRecordingLevel;
    }

    @JsonPropertyDescription("A list of classes to use as metrics reporters. Implementing the " +
            "<code>org.apache.kafka.common.metrics.MetricsReporter</code> interface allows plugging in classes " +
            "that will be notified of new metric creation. The JmxReporter is always included to register JMX " +
            "statistics.")
    @JsonProperty
    public List<String> getMetricReporters() {
        return metricReporters;
    }

    @JsonPropertyDescription("The maximum number of unacknowledged requests the client will send on a single " +
            "connection before blocking."
            + " Note that if this setting is set to be greater than 1 and there are failed sends, there is a risk of"
            + " message re-ordering due to retries (i.e., if retries are enabled).")
    @JsonProperty
    public Integer getMaxInFlightRequestsPerConnection() {
        return maxInFlightRequestsPerConnection;
    }

    @JsonPropertyDescription("Setting a value greater than zero will cause the client to resend any record whose " +
            "send fails with a potentially transient error."
            + " Note that this retry is no different than if the client resent the record upon receiving the error."
            + " Allowing retries without setting <code>maxInFlightRequestsPerConnection</code> to 1 will " +
            "potentially change the"
            + " ordering of records because if two batches are sent to a single partition, and the first fails " +
            "and is retried but the second"
            + " succeeds, then the records in the second batch may appear first. Note additionall that produce " +
            "requests will be"
            + " failed before the number of retries has been exhausted if the timeout configured by"
            + " <code>deliveryTimeoutMs</code> expires first before successful acknowledgement. Users should generally"
            + " prefer to leave this config unset and instead use <code>deliveryTimeoutMs</code> to control"
            + " retry behavior.")
    @JsonProperty
    public Integer getRetries() {
        return retries;
    }

    @JsonPropertyDescription("Serializer class for key that implements the " +
            "<code>org.apache.kafka.common.serialization.Serializer</code> interface.")
    @JsonProperty
    public String getKeySerializer() {
        return keySerializer;
    }

    @JsonPropertyDescription("Serializer class for value that implements the " +
            "<code>org.apache.kafka.common.serialization.Serializer</code> interface.")
    @JsonProperty
    public String getValueSerializer() {
        return valueSerializer;
    }

    @JsonPropertyDescription("Close idle connections after the number of milliseconds specified by this config.")
    @JsonProperty
    public Long getConnectionsMaxIdleMs() {
        return connectionsMaxIdleMs;
    }

    @JsonPropertyDescription("Partitioner class that implements the " +
            "<code>org.apache.kafka.clients.producer.Partitioner</code> interface.")
    @JsonProperty
    public String getPartitionerClass() {
        return partitionerClass;
    }

    @JsonPropertyDescription("A list of classes to use as interceptors. "
            + "Implementing the <code>org.apache.kafka.clients.producer.ProducerInterceptor</code> interface " +
            "allows you to intercept (and possibly mutate) the records "
            + "received by the producer before they are published to the Kafka cluster. By default, there are " +
            "no interceptors.")
    @JsonProperty
    public List<String> getInterceptorClasses() {
        return interceptorClasses;
    }

    @JsonPropertyDescription("When set to 'true', the producer will ensure that exactly one copy of each message " +
            "is written in the stream. If 'false', producer "
            + "retries due to broker failures, etc., may write duplicates of the retried message in the stream. "
            + "Note that enabling idempotence requires <code>maxInFlightRequestsPerConnection</code> to be less " +
            "than or equal to 5, "
            + "<code>retries</code> to be greater than 0 and <code>acks</code> must be 'all'. If these values "
            + "are not explicitly set by the user, suitable values will be chosen. If incompatible values are set, "
            + "a <code>ConfigException</code> will be thrown.")
    @JsonProperty
    public Boolean getEnableIdempotence() {
        return enableIdempotence;
    }

    @JsonPropertyDescription("The maximum amount of time in ms that the transaction coordinator will wait for a " +
            "transaction status update from the producer before proactively aborting the ongoing transaction." +
            "If this value is larger than the transaction.max.timeout.ms setting in the broker, the request will " +
            "fail with a <code>InvalidTransactionTimeout</code> error.")
    @JsonProperty
    public Integer getTransactionTimeoutMs() {
        return transactionTimeoutMs;
    }

    @JsonPropertyDescription("The TransactionalId to use for transactional delivery. This enables reliability " +
            "semantics which span multiple producer sessions since it allows the client to guarantee that " +
            "transactions using the same TransactionalId have been completed prior to starting any new " +
            "transactions. If no TransactionalId is provided, then the producer is limited to idempotent delivery. " +
            "Note that <code>enable.idempotence</code> must be enabled if a TransactionalId is configured. " +
            "The default is <code>null</code>, which means transactions cannot be used. " +
            "Note that, by default, transactions require a cluster of at least three brokers which is the " +
            "recommended setting for production; for development you can change this, by adjusting broker " +
            "setting <code>transaction.state.log.replication.factor</code>.")
    @JsonProperty
    public String getTransactionalId() {
        return transactionalId;
    }
}
