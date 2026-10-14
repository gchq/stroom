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

package stroom.ai.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/// The state of a chat's compaction, as stored (as JSON) in a COMPACTION_SUMMARY message. Turns are identified
/// by the id of the question that started them, so they are in order.
///
/// The model sees: the summary, in place of every turn up to {@code summaryUpToTurnId}; then the digest of each
/// turn after that, up to {@code digestsUpToTurnId}; then every later turn in full.
///
/// @param digestsUpToTurnId The last turn the model sees as a digest (or summary), or 0 for none.
/// @param summaryUpToTurnId The last turn covered by the summary, or 0 for none.
/// @param summary           A summary written by the model of the turns up to {@code summaryUpToTurnId}, or null.
///                          May be null even if {@code summaryUpToTurnId} is set, if summarising failed and
///                          the oldest digests were dropped instead.
/// @param tokensBefore      The estimated tokens the model would have been sent before this compaction.
/// @param tokensAfter       The estimated tokens it is sent after it.
/// @param messageCount      How many messages the model now sees as digests or a summary.
/// @param manual            True if the user asked for it, false if it was automatic.
/// @param stale             True if a message it covers has since been changed or deleted, so it is no longer
///                          used.
@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public record CompactionRecord(@JsonProperty("digestsUpToTurnId") int digestsUpToTurnId,
                               @JsonProperty("summaryUpToTurnId") int summaryUpToTurnId,
                               @JsonProperty("summary") String summary,
                               @JsonProperty("tokensBefore") long tokensBefore,
                               @JsonProperty("tokensAfter") long tokensAfter,
                               @JsonProperty("messageCount") int messageCount,
                               @JsonProperty("manual") boolean manual,
                               @JsonProperty("stale") boolean stale) {

    /// Nothing compacted.
    public static final CompactionRecord NONE = new CompactionRecord(0, 0, null, 0, 0, 0, false, false);

    @JsonCreator
    public CompactionRecord {
        // Explicit constructor for Jackson.
    }

    /// @return A copy that shows turns up to {@code turnId} as digests.
    public CompactionRecord withDigestsUpTo(final int turnId) {
        return new CompactionRecord(turnId, summaryUpToTurnId, summary, tokensBefore, tokensAfter, messageCount,
                manual, stale);
    }

    /// @return A copy with the turns up to {@code turnId} replaced by {@code summary}.
    public CompactionRecord withSummary(final String summary, final int turnId) {
        return new CompactionRecord(digestsUpToTurnId, turnId, summary, tokensBefore, tokensAfter, messageCount,
                manual, stale);
    }

    /// @return A copy with the figures shown to the user.
    public CompactionRecord withStats(final long tokensBefore,
                                      final long tokensAfter,
                                      final int messageCount,
                                      final boolean manual) {
        return new CompactionRecord(digestsUpToTurnId, summaryUpToTurnId, summary, tokensBefore, tokensAfter,
                messageCount, manual, stale);
    }

    /// @return A copy marked as stale.
    public CompactionRecord asStale() {
        return new CompactionRecord(digestsUpToTurnId, summaryUpToTurnId, summary, tokensBefore, tokensAfter,
                messageCount, manual, true);
    }

    /// @param other Another record, or null.
    /// @return True if the model would see the same thing with either record.
    public boolean coversTheSameAs(final CompactionRecord other) {
        return other != null
               && digestsUpToTurnId == other.digestsUpToTurnId
               && summaryUpToTurnId == other.summaryUpToTurnId
               && Objects.equals(summary, other.summary);
    }

    /// @param turnId A turn.
    /// @return True if the model sees the turn as a digest or summary rather than in full.
    public boolean covers(final int turnId) {
        return turnId <= digestsUpToTurnId;
    }
}
