/*
 * Copyright 2016 Crown Copyright
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

package stroom.streamtask;

import stroom.streamstore.shared.QueryData;
import stroom.streamtask.shared.ProcessorFilter;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Holds info about streams seen since last query We use this to help only
 * create tasks where we have seen a recent stream that will match. That way a
 * stream processor for a rare feed will only be updated when a stream comes in
 * for that feed.
 */
public class StreamTaskCreatorRecentStreamDetails {
    // Oldest stream id we have started recording from
    private final Long earliestStreamId;
    // Last max stream id
    private final Long recentStreamId;
    // Current max stream id
    private final long maxStreamId;
    // All feeds we have seen come in since we started recording
    private final Set<Long> earliestFeedIdSet = new HashSet<>();
    // New feeds since recentStreamId and maxStreamId
    private final Set<Long> recentFeedIdSet = new HashSet<>();

    public StreamTaskCreatorRecentStreamDetails(final StreamTaskCreatorRecentStreamDetails lastRecentStreamInfo,
                                                final long maxStreamId) {
        if (lastRecentStreamInfo != null) {
            this.earliestStreamId = lastRecentStreamInfo.earliestStreamId;
            this.recentStreamId = lastRecentStreamInfo.maxStreamId;
            this.earliestFeedIdSet.addAll(lastRecentStreamInfo.getEarliestFeedIdSet());
            this.earliestFeedIdSet.addAll(lastRecentStreamInfo.getRecentFeedIdSet());
        } else {
            this.earliestStreamId = maxStreamId;
            this.recentStreamId = null;
        }
        this.maxStreamId = maxStreamId;
    }

    public boolean hasRecentDetail() {
        return recentStreamId != null && maxStreamId != 0;
    }

    public long getRecentStreamCount() {
        return maxStreamId - recentStreamId.longValue();
    }

    public void addRecentFeedId(final Long recentFeedId) {
        this.recentFeedIdSet.add(recentFeedId);
    }

    public long getMaxStreamId() {
        return maxStreamId;
    }

    public Long getRecentStreamId() {
        return recentStreamId;
    }

    public Set<Long> getRecentFeedIdSet() {
        return Collections.unmodifiableSet(recentFeedIdSet);
    }

    public Set<Long> getEarliestFeedIdSet() {
        return Collections.unmodifiableSet(earliestFeedIdSet);
    }

    /**
     * Given this RecentStreamInfo will this filter criteria pick anything up?
     */
    public boolean isApplicable(final ProcessorFilter filter, final QueryData findStreamCriteria) {
        return true;

//        // No history so yes
//        if (earliestStreamId == null || recentStreamId == null) {
//            return true;
//        }
//        // Filter before what we think is recent.
//        if (filter.getStreamProcessorFilterTracker().getMinStreamId() <= earliestStreamId.longValue()) {
//            return true;
//        }
//        // Not filtered by feed ?
//        if (findStreamCriteria.getFeeds() == null || !findStreamCriteria.getFeeds().isConstrained()) {
//            return true;
//        }
//        if (findStreamCriteria.getFeeds().getInclude() == null
//                || !findStreamCriteria.getFeeds().getInclude().isConstrained()) {
//            return true;
//        }
//
//        for (final Long feedId : findStreamCriteria.getFeeds().getInclude()) {
//            if (recentFeedIdSet.contains(feedId)) {
//                return true;
//            }
//        }
//        return false;
    }

    @Override
    public String toString() {
        return "earliestStreamId=" + earliestStreamId + ", recentStreamId=" + recentStreamId + ", maxStreamId="
                + maxStreamId + ", earliestFeedIdSet=" + earliestFeedIdSet + ", recentFeedIdSet=" + recentFeedIdSet;
    }
}
