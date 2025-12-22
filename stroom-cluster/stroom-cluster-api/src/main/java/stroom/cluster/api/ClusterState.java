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

package stroom.cluster.api;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

public class ClusterState {

    // Using ConcurrentMap<String, Boolean> rather than Set<String> to give us access
    // to the concurrent api. The boolean value is not used and is always true.
    private final ConcurrentMap<String, Boolean> allNodes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> enabledActiveNodes = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Boolean> enabledNodes = new ConcurrentHashMap<>();

    private final AtomicReference<String> masterNodeNameRef = new AtomicReference<>(null);
    private volatile long updateTime;

    public Set<String> getAllNodes() {
        return allNodes.keySet();
    }

    public void setAllNodes(final Collection<String> allNodes) {
        this.allNodes.keySet()
                .retainAll(allNodes);
        allNodes.forEach(node ->
                this.allNodes.putIfAbsent(node, Boolean.TRUE));
    }

    public Set<String> getEnabledActiveNodes() {
        return enabledActiveNodes.keySet();
    }

    public boolean isEnabledAndActive(final String nodeName) {
        return enabledActiveNodes.containsKey(nodeName);
    }

    public boolean isEnabled(final String nodeName) {
        return enabledNodes.containsKey(nodeName);
    }

    public Set<String> getEnabledNodes() {
        return enabledNodes.keySet();
    }

    public void setEnabledNodes(final Collection<String> enabledNodes) {
        // Keep the ones still enabled
        this.enabledNodes.keySet()
                .retainAll(enabledNodes);
        // Add any we didn't have
        enabledNodes.forEach(enabledNode ->
                this.enabledNodes.putIfAbsent(enabledNode, Boolean.TRUE));
    }

    public String getMasterNodeName() {
        return masterNodeNameRef.get();
    }

    /**
     * Sets the master node name to the new value and returns the old value
     */
    public String setMasterNodeName(final String masterNodeName) {
        final String oldMasterNodeName = this.masterNodeNameRef.getAndSet(masterNodeName);
        return oldMasterNodeName;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(final long updateTime) {
        this.updateTime = updateTime;
    }

    public void retainEnabledActiveNodes(final Collection<String> enabledNodes) {
        enabledActiveNodes.keySet()
                .retainAll(enabledNodes);
    }

    public void addEnabledActiveNode(final String nodeName) {
        enabledActiveNodes.putIfAbsent(nodeName, Boolean.TRUE);
    }

    public void removeEnabledActiveNode(final String nodeName) {
        enabledActiveNodes.remove(nodeName);
    }
}
