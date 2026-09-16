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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * What was submitted, what came out of the far end, and how many times.
 * <p>
 * The pipeline's contract is <strong>at-least-once</strong>. That makes the
 * invariants asymmetric and it is worth being explicit about which way round
 * they go, because getting this wrong produces a test suite that fails on
 * correct behaviour:
 * </p>
 * <ul>
 *   <li><strong>No loss.</strong> Every submitted payload must be delivered at
 *       least once. This is the invariant the pipeline actually promises and the
 *       one worth failing a build over.</li>
 *   <li><strong>No corruption.</strong> Every delivery must be intact. Injected
 *       faults may duplicate work; they must never produce a half-copied group,
 *       and a stage must never publish a reference to data it did not finish
 *       writing.</li>
 *   <li><strong>No invention.</strong> Nothing may be delivered that was never
 *       submitted.</li>
 *   <li><strong>Duplicates are recorded, not asserted against.</strong> A
 *       duplicate is the correct outcome of a fault between publishing and
 *       deleting. Scenarios report the duplicate count so a change in
 *       duplication behaviour is visible, but only a baseline run with no faults
 *       injected is entitled to demand exactly-once.</li>
 * </ul>
 */
public final class DeliveryLedger {

    private final Set<String> submitted = new ConcurrentSkipListSet<>();
    private final Map<String, AtomicInteger> deliveryCounts = new ConcurrentHashMap<>();
    private final List<String> corruptions = new ArrayList<>();

    public void recordSubmitted(final String payloadId) {
        submitted.add(payloadId);
    }

    /**
     * Record what the terminal stage saw.
     *
     * @param read The payload as read back at the end of the pipeline.
     */
    public void recordDelivered(final StressPayload.Read read) {
        if (read.payloadId() != null) {
            deliveryCounts
                    .computeIfAbsent(read.payloadId(), ignored -> new AtomicInteger())
                    .incrementAndGet();
        }

        if (!read.intact()) {
            synchronized (corruptions) {
                corruptions.add(read.problem());
            }
        }
    }

    public int getSubmittedCount() {
        return submitted.size();
    }

    /**
     * @return The number of distinct payloads delivered at least once.
     */
    public int getDeliveredCount() {
        return deliveryCounts.size();
    }

    /**
     * @return Total deliveries including repeats.
     */
    public int getTotalDeliveryCount() {
        int total = 0;
        for (final AtomicInteger count : deliveryCounts.values()) {
            total += count.get();
        }
        return total;
    }

    /**
     * @return Deliveries beyond the first for each payload.
     */
    public int getDuplicateDeliveryCount() {
        return getTotalDeliveryCount() - getDeliveredCount();
    }

    /**
     * @return Submitted payloads that never arrived - the loss set.
     */
    public Set<String> getLost() {
        final Set<String> lost = new TreeSet<>(submitted);
        lost.removeAll(deliveryCounts.keySet());
        return lost;
    }

    /**
     * @return Delivered payloads that were never submitted.
     */
    public Set<String> getUnexpected() {
        final Set<String> unexpected = new LinkedHashSet<>(deliveryCounts.keySet());
        unexpected.removeAll(submitted);
        return unexpected;
    }

    public List<String> getCorruptions() {
        synchronized (corruptions) {
            return List.copyOf(corruptions);
        }
    }

    public boolean isFullyDelivered() {
        return deliveryCounts.keySet().containsAll(submitted);
    }

    public String describe() {
        return "submitted=" + getSubmittedCount()
               + ", deliveredDistinct=" + getDeliveredCount()
               + ", deliveriesTotal=" + getTotalDeliveryCount()
               + ", duplicates=" + getDuplicateDeliveryCount()
               + ", lost=" + getLost().size()
               + ", corruptions=" + getCorruptions().size();
    }
}
