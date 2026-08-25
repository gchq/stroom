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

package stroom.proxy.app.pipeline.stress;

/**
 * Where the stress harness can inject a failure.
 * <p>
 * The points ending in {@code _AFTER} are the interesting ones. They let the
 * underlying operation succeed and <em>then</em> throw, modelling a process that
 * dies between an effect becoming durable and the caller learning that it did.
 * That is the gap the pipeline's ownership-transfer contract is built around -
 * write output, publish, delete input, acknowledge - and the reason the contract
 * is at-least-once rather than exactly-once. A harness that only injects
 * before-the-effect faults never produces a duplicate and so never tests the
 * part of the contract most likely to be got wrong.
 * </p>
 */
public enum FaultPoint {

    /**
     * Fail before the message reaches the queue. The publisher must treat its
     * output as unpublished.
     */
    QUEUE_PUBLISH,

    /**
     * Publish succeeds, then fail. The producing stage will not have deleted its
     * input, so it reprocesses and publishes a second message for the same file
     * group. Duplicate downstream work is the expected, correct outcome.
     */
    QUEUE_PUBLISH_AFTER,

    /**
     * Fail while leasing the next item.
     */
    QUEUE_NEXT,

    /**
     * Fail before the acknowledgement is recorded. The item stays in-flight and
     * must be recovered when the queue is reopened.
     */
    QUEUE_ACK,

    /**
     * Acknowledge succeeds, then fail. The worker sees an ack error for work that
     * actually completed; nothing may be lost as a result.
     */
    QUEUE_ACK_AFTER,

    /**
     * Fail while returning an item to the queue after a processing error. The
     * item must not vanish.
     */
    QUEUE_FAIL,

    /**
     * Fail before a writable location is allocated.
     */
    STORE_NEW_WRITE,

    /**
     * Fail before the write is made visible.
     */
    STORE_COMMIT,

    /**
     * Commit succeeds, then fail. Committed but unreferenced data is left in the
     * store - an orphan. Orphans cost disk; they must never be mistaken for
     * delivered data.
     */
    STORE_COMMIT_AFTER,

    /**
     * Fail while releasing a consumed input. The input stays behind and will be
     * reprocessed.
     */
    STORE_DELETE,

    /**
     * Fail while resolving a location to a path.
     */
    STORE_RESOLVE;

    /**
     * @return True if this point fails only after the underlying effect has
     * already happened.
     */
    public boolean isAfterEffect() {
        return this == QUEUE_PUBLISH_AFTER
               || this == QUEUE_ACK_AFTER
               || this == STORE_COMMIT_AFTER;
    }
}
