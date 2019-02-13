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

package stroom.cluster.api;

import java.util.Collections;
import java.util.Set;

public class ClusterState {
    private transient Set<String> allNodes = Collections.emptySet();
    private transient Set<String> enabledActiveNodes = Collections.emptySet();
    private transient Set<String> enabledNodes = Collections.emptySet();
    private transient String masterNodeName;
    private transient long updateTime;

    public Set<String> getAllNodes() {
        return allNodes;
    }

    public void setAllNodes(final Set<String> allNodes) {
        this.allNodes = Collections.unmodifiableSet(allNodes);
    }

    public Set<String> getEnabledActiveNodes() {
        return enabledActiveNodes;
    }

    public void setEnabledActiveNodes(final Set<String> enabledActiveNodes) {
        this.enabledActiveNodes = Collections.unmodifiableSet(enabledActiveNodes);
    }

    public Set<String> getEnabledNodes() {
        return enabledNodes;
    }

    public void setEnabledNodes(final Set<String> enabledNodes) {
        this.enabledNodes = Collections.unmodifiableSet(enabledNodes);
    }

    public String getMasterNodeName() {
        return masterNodeName;
    }

    public void setMasterNodeName(String masterNodeName) {
        this.masterNodeName = masterNodeName;
    }

    public long getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(long updateTime) {
        this.updateTime = updateTime;
    }
}
