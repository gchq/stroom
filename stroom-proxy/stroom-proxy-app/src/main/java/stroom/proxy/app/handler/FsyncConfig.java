/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.handler;

import stroom.util.config.annotations.RequiresProxyRestart;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsProxyConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// Controls whether data is forced to durable storage (`fsync`) as it passes through each
/// phase of the proxy pipeline.
///
/// Without this, data that has been written and acknowledged may still only be in the
/// operating system's page cache and can be lost if the machine loses power.
///
/// Every phase after receipt is carrying data the sender has already been told is safe, so all
/// of them matter. They are configured separately because what they cost differs markedly, not
/// because some are optional:
///
/// * [#isReceiving()] is the expensive one, as it forces the whole of the received payload to
///   disk before a receipt response is returned to the sender. This is what makes the receipt
///   honest; a crash before the response is sent costs nothing, as the sender simply retries.
/// * The queue settings are close to free. They force the directory entry created by the atomic
///   move that commits an item into the queue, not the item's contents, so they are a small
///   metadata flush rather than a flush of the data itself.
///
/// The practical consequence is that turning off a queue setting saves very little, whereas
/// turning off [#isReceiving()] buys back real throughput at the cost of the receipt guarantee.
/// That is the one to consider changing if receipt latency matters more than durability.
///
/// Note that [#isReceiving()] also covers data that is rewritten after receipt, by the zip
/// splitter and the aggregator. Those phases write new files and then delete the originals that
/// were forced on receipt, so without forcing the rewritten output the receipt guarantee would be
/// lost partway through the pipeline.
@JsonPropertyOrder(alphabetic = true)
public class FsyncConfig extends AbstractConfig implements IsProxyConfig {

    public static final boolean DEFAULT_IS_RECEIVING_ENABLED = true;
    public static final boolean DEFAULT_IS_ZIP_SPLITTING_INPUT_QUEUE_ENABLED = true;
    public static final boolean DEFAULT_IS_PRE_AGGREGATE_INPUT_QUEUE_ENABLED = true;
    public static final boolean DEFAULT_IS_AGGREGATE_INPUT_QUEUE_ENABLED = true;
    public static final boolean DEFAULT_IS_FORWARDING_INPUT_QUEUE_ENABLED = true;

    private final boolean receiving;
    private final boolean zipSplittingInputQueue;
    private final boolean preAggregateInputQueue;
    private final boolean aggregateInputQueue;
    private final boolean forwardingInputQueue;

    public FsyncConfig() {
        receiving = DEFAULT_IS_RECEIVING_ENABLED;
        zipSplittingInputQueue = DEFAULT_IS_ZIP_SPLITTING_INPUT_QUEUE_ENABLED;
        preAggregateInputQueue = DEFAULT_IS_PRE_AGGREGATE_INPUT_QUEUE_ENABLED;
        aggregateInputQueue = DEFAULT_IS_AGGREGATE_INPUT_QUEUE_ENABLED;
        forwardingInputQueue = DEFAULT_IS_FORWARDING_INPUT_QUEUE_ENABLED;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public FsyncConfig(
            @JsonProperty("receiving") final Boolean receiving,
            @JsonProperty("zipSplittingInputQueue") final Boolean zipSplittingInputQueue,
            @JsonProperty("preAggregateInputQueue") final Boolean preAggregateInputQueue,
            @JsonProperty("aggregateInputQueue") final Boolean aggregateInputQueue,
            @JsonProperty("forwardingInputQueue") final Boolean forwardingInputQueue) {

        this.receiving = Objects.requireNonNullElse(
                receiving, DEFAULT_IS_RECEIVING_ENABLED);
        this.zipSplittingInputQueue = Objects.requireNonNullElse(
                zipSplittingInputQueue, DEFAULT_IS_ZIP_SPLITTING_INPUT_QUEUE_ENABLED);
        this.preAggregateInputQueue = Objects.requireNonNullElse(
                preAggregateInputQueue, DEFAULT_IS_PRE_AGGREGATE_INPUT_QUEUE_ENABLED);
        this.aggregateInputQueue = Objects.requireNonNullElse(
                aggregateInputQueue, DEFAULT_IS_AGGREGATE_INPUT_QUEUE_ENABLED);
        this.forwardingInputQueue = Objects.requireNonNullElse(
                forwardingInputQueue, DEFAULT_IS_FORWARDING_INPUT_QUEUE_ENABLED);
    }

    @JsonPropertyDescription("If true, received data is forced to durable storage before a " +
                             "receipt response is returned to the sender, as is any data rewritten " +
                             "from it by the zip splitter or the aggregator. Turning this off means " +
                             "the proxy may acknowledge data that is subsequently lost if the " +
                             "machine loses power, but it will receive data faster.")
    @RequiresProxyRestart
    @JsonProperty
    public boolean isReceiving() {
        return receiving;
    }

    @JsonPropertyDescription("If true, entries added to the zip splitting input queue are forced " +
                             "to durable storage.")
    @RequiresProxyRestart
    @JsonProperty
    public boolean isZipSplittingInputQueue() {
        return zipSplittingInputQueue;
    }

    @JsonPropertyDescription("If true, entries added to the pre-aggregate input queue are forced " +
                             "to durable storage.")
    @RequiresProxyRestart
    @JsonProperty
    public boolean isPreAggregateInputQueue() {
        return preAggregateInputQueue;
    }

    @JsonPropertyDescription("If true, entries added to the aggregate input queue are forced " +
                             "to durable storage.")
    @RequiresProxyRestart
    @JsonProperty
    public boolean isAggregateInputQueue() {
        return aggregateInputQueue;
    }

    @JsonPropertyDescription("If true, entries added to the forwarding input queue, and to the " +
                             "forward and retry queues of each forward destination, are forced to " +
                             "durable storage.")
    @RequiresProxyRestart
    @JsonProperty
    public boolean isForwardingInputQueue() {
        return forwardingInputQueue;
    }

    @Override
    public String toString() {
        return "FsyncConfig{" +
               "receiving=" + receiving +
               ", zipSplittingInputQueue=" + zipSplittingInputQueue +
               ", preAggregateInputQueue=" + preAggregateInputQueue +
               ", aggregateInputQueue=" + aggregateInputQueue +
               ", forwardingInputQueue=" + forwardingInputQueue +
               '}';
    }
}
