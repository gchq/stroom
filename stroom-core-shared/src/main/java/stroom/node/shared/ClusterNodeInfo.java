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

package stroom.node.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.util.shared.BuildInfo;

import java.util.ArrayList;
import java.util.List;

@JsonInclude(Include.NON_DEFAULT)
public class ClusterNodeInfo {
    @JsonProperty
    private String discoverTime;
    @JsonProperty
    private BuildInfo buildInfo;
    @JsonProperty
    private String nodeName;
    @JsonProperty
    private String clusterURL;
    @JsonProperty
    private List<ClusterNodeInfoItem> itemList = new ArrayList<>();
    @JsonProperty
    private Long ping;
    @JsonProperty
    private String error;

    public ClusterNodeInfo() {
    }

    public ClusterNodeInfo(final String discoverTime, final BuildInfo buildInfo, final String nodeName, final String clusterURL) {
        this.discoverTime = discoverTime;
        this.buildInfo = buildInfo;
        this.nodeName = nodeName;
        this.clusterURL = clusterURL;
    }

    @JsonCreator
    public ClusterNodeInfo(@JsonProperty("discoverTime") final String discoverTime,
                           @JsonProperty("buildInfo") final BuildInfo buildInfo,
                           @JsonProperty("nodeName") final String nodeName,
                           @JsonProperty("clusterURL") final String clusterURL,
                           @JsonProperty("itemList") final List<ClusterNodeInfoItem> itemList,
                           @JsonProperty("ping") final Long ping,
                           @JsonProperty("error") final String error) {
        this.discoverTime = discoverTime;
        this.buildInfo = buildInfo;
        this.nodeName = nodeName;
        this.clusterURL = clusterURL;
        this.itemList = itemList;
        this.ping = ping;
        this.error = error;
    }

    public void addItem(final String nodeName, final boolean active, final boolean master) {
        itemList.add(new ClusterNodeInfoItem(nodeName, active, master));
    }

    public String getDiscoverTime() {
        return discoverTime;
    }

    public void setDiscoverTime(final String discoverTime) {
        this.discoverTime = discoverTime;
    }

    public BuildInfo getBuildInfo() {
        return buildInfo;
    }

    public void setBuildInfo(final BuildInfo buildInfo) {
        this.buildInfo = buildInfo;
    }

    public String getNodeName() {
        return nodeName;
    }

    public void setNodeName(final String nodeName) {
        this.nodeName = nodeName;
    }

    public String getClusterURL() {
        return clusterURL;
    }

    public void setClusterURL(final String clusterURL) {
        this.clusterURL = clusterURL;
    }

    public List<ClusterNodeInfoItem> getItemList() {
        return itemList;
    }

    public void setItemList(final List<ClusterNodeInfoItem> itemList) {
        this.itemList = itemList;
    }

    public Long getPing() {
        return ping;
    }

    public void setPing(final Long ping) {
        this.ping = ping;
    }

    public String getError() {
        return error;
    }

    public void setError(final String error) {
        this.error = error;
    }

    @JsonInclude(Include.NON_DEFAULT)
    public static class ClusterNodeInfoItem {
        @JsonProperty
        private final String nodeName;
        @JsonProperty
        private final boolean active;
        @JsonProperty
        private final boolean master;

        @JsonCreator
        public ClusterNodeInfoItem(@JsonProperty("nodeName") final String nodeName,
                                   @JsonProperty("active") final boolean active,
                                   @JsonProperty("master") final boolean master) {
            this.nodeName = nodeName;
            this.active = active;
            this.master = master;
        }

        public String getNodeName() {
            return nodeName;
        }

        public boolean isActive() {
            return active;
        }

        public boolean isMaster() {
            return master;
        }
    }
}
