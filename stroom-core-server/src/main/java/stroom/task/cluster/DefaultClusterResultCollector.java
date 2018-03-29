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

package stroom.task.cluster;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.node.shared.Node;
import stroom.util.shared.SharedObject;

import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class DefaultClusterResultCollector<R extends SharedObject> implements ClusterResultCollector<R> {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultClusterResultCollector.class);

    private final CollectorId id;
    private final ClusterTask<R> request;
    private final Node sourceNode;
    private final Set<Node> targetNodes;
    private final long startTimeMs;

    /**
     * A map of each node to it's response
     */
    private final ConcurrentHashMap<Node, ClusterCallEntry<R>> responseMap = new ConcurrentHashMap<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();

    private volatile long waitTimeNS;
    private volatile long waitTimeRemainingNS;
    private volatile boolean receivedResult;
    private volatile boolean terminated;

    public DefaultClusterResultCollector(final ClusterTask<R> request, final Node sourceNode,
                                         final Set<Node> targetNodes) {
        id = CollectorIdFactory.create();
        this.request = request;
        this.sourceNode = sourceNode;
        this.targetNodes = targetNodes;
        this.startTimeMs = System.currentTimeMillis();
    }

    @Override
    public CollectorId getId() {
        return id;
    }

    /**
     * When this node receives a result we should stop the remaining wait time
     * being reduced if we are waiting on a result. Return true if we are going
     * to be able to successfully handle the result.
     *
     * @see stroom.task.cluster.ClusterResultCollector#onReceive()
     */
    @Override
    public boolean onReceive() {
        // Record that we have received a result so that we don't reduce the
        // wait time if we are waiting.
        receivedResult = true;

        boolean ok = true;

        // If the collector has been told to wait and the remaining wait time is
        // greater than 0 then we are sure to deliver a result.
        if (waitTimeNS > 0) {
            if (waitTimeRemainingNS <= 0) {
                // It's too late to get this result as we must have stopped
                // waiting.
                ok = false;
            }
        }

        return ok;
    }

    @Override
    public void onSuccess(final Node node, final R r) {
        responseMap.put(node, new ClusterCallEntry<>(r, null, System.currentTimeMillis() - startTimeMs));
        lock.lock();
        try {
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onFailure(final Node node, final Throwable throwable) {
        responseMap.put(node, new ClusterCallEntry<>(null, throwable, System.currentTimeMillis() - startTimeMs));
        lock.lock();
        try {
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void terminate() {
        terminated = true;
        lock.lock();
        try {
            condition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public ClusterCallEntry<R> getSingleResponse() {
        final Iterator<ClusterCallEntry<R>> itr = responseMap.values().iterator();
        if (itr.hasNext()) {
            return itr.next();
        }
        return null;
    }

    public ClusterCallEntry<R> getResponse(final Node node) {
        return responseMap.get(node);
    }

    public Map<Node, ClusterCallEntry<R>> getResponseMap() {
        return responseMap;
    }

    private boolean isComplete() {
        return terminated || targetNodes.size() == responseMap.size();
    }

    public void waitToComplete(final long waitTime, final TimeUnit timeUnit) {
        this.waitTimeNS = TimeUnit.NANOSECONDS.convert(waitTime, timeUnit);
        this.waitTimeRemainingNS = waitTimeNS;

        lock.lock();
        try {
            while (!terminated && !isComplete() && waitTimeRemainingNS > 0) {
                waitTimeRemainingNS = condition.awaitNanos(waitTimeRemainingNS);

                // Reset the wait time if we have received a result so that
                // we keep waiting until complete.
                if (receivedResult) {
                    receivedResult = false;
                    waitTimeRemainingNS = waitTimeNS;
                }
            }

            if (waitTimeRemainingNS <= 0) {
                LOGGER.debug("waitToComplete() - Timeout");
            }
        } catch (final InterruptedException e) {
            waitTimeRemainingNS = 0;
            LOGGER.error(e.getMessage(), e);

            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }

        LOGGER.debug("waitToComplete() - Finished complete is {}", isComplete());
    }

    public Set<Node> getTargetNodes() {
        return targetNodes;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        appendDetails(builder);
        builder.append(" complete=");
        builder.append(isComplete());
        return builder.toString();
    }

    private void appendDetails(final StringBuilder builder) {
        builder.append(sourceNode.getName());
        builder.append(" -> (");
        if (targetNodes != null) {
            for (final Node target : targetNodes) {
                builder.append(target.getName());
                builder.append(",");
            }
        }
        builder.setLength(builder.length() - 1);
        builder.append(") ");
        builder.append("request=");
        builder.append(request);
    }
}
