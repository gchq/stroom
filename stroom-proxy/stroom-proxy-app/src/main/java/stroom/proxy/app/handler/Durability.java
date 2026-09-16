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

/**
 * How much the proxy forces to stable storage before it treats a step as committed.
 * <p>
 * <strong>R1 requires power-loss durability; the cost of providing it is configurable.</strong>
 * The invariant itself is not negotiable and is the same in every mode — at each power-loss point,
 * either the input is still claimable or the output is durable (contracts.md
 * <a href="../../../../../../../../designs/contracts.md">§6</a>). What a mode chooses is
 * <em>who guarantees it</em>: this process, or the filesystem underneath it.
 * </p>
 * <p>
 * The distinction that makes the middle mode worth having is one of size. A queue message is a few
 * hundred bytes and is what makes committed data <em>findable</em>; a file group is arbitrarily large
 * and is the data itself. Losing the message strands the data with nothing pointing at it, so
 * forcing the message is cheap and buys the most.
 * </p>
 * <p>
 * <strong>An ordered filesystem is what the weaker modes rest on.</strong> A journalling filesystem
 * mounted with ordered data (ext4's default {@code data=ordered}, XFS) writes file data before the
 * metadata that publishes it, so a rename cannot become visible ahead of the bytes it names. That is
 * not a guarantee the proxy can check, and it is not true of every mount — which is why anything
 * below {@link #FULL} warns at start-up.
 * </p>
 */
public enum Durability {

    /**
     * Force everything: the file group's files and the directory entries that publish them, and the
     * queue message and its directory entry. The default, and the only mode that does not depend on
     * the filesystem's own ordering.
     */
    FULL,

    /**
     * Force the queue message and its directory entry; rely on the filesystem's ordering for file
     * group payloads.
     * <p>
     * The trade is deliberate: the small, cheap thing that makes data findable is guaranteed by this
     * process, and the large, expensive thing is left to an ordered mount. On a machine where an
     * fsync per file group costs more than it is worth, this keeps the failure mode "data may be
     * incomplete after a power cut" rather than "data is unreachable after a power cut".
     * </p>
     */
    QUEUE_ONLY,

    /**
     * Force nothing; rely on the mount's ordering throughout.
     * <p>
     * Correct only where the storage genuinely provides it — a battery-backed controller, or an
     * object store whose acknowledgement already <em>is</em> the durability point
     * (contracts.md §6.1), where a local fsync guarantees nothing that matters.
     * </p>
     */
    FILESYSTEM;

    /**
     * @return Whether a file group's own bytes must be forced before the rename that publishes them.
     */
    public boolean forcesFileGroups() {
        return this == FULL;
    }

    /**
     * @return Whether a queue message and the directory entry that publishes it must be forced.
     */
    public boolean forcesQueueMessages() {
        return this == FULL || this == QUEUE_ONLY;
    }

    /**
     * @return Whether an event must be forced before its receipt id is returned to the sender.
     * <p>
     * The {@code /event} endpoint returns a receipt id as soon as the event is
     * written and flushed to a {@code BufferedOutputStream} — flushed to the OS, not to the disk.
     * That is an acknowledgement of data that a power cut can still take, which R1 does not allow,
     * and it was the only acknowledged ingest path P7's durability work did not cover.
     * </p>
     * <p>
     * Grouped with queue messages rather than file groups because it shares their shape: an event is
     * a few hundred bytes, and it is the acknowledgement that makes it the proxy's responsibility. By
     * the same argument that makes forcing a queue message worth its cost, forcing an event is too.
     * </p>
     */
    public boolean forcesEvents() {
        return this == FULL || this == QUEUE_ONLY;
    }
}
