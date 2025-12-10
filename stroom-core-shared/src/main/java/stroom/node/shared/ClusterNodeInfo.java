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

package stroom.node.shared;

import stroom.util.shared.BuildInfo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class ClusterNodeInfo {

    @JsonProperty
    private long discoverTime;
    @JsonProperty
    private BuildInfo buildInfo;
    @JsonProperty
    private String nodeName;
    @JsonProperty
    private String endpointUrl;
    @JsonProperty
    private List<ClusterNodeInfoItem> itemList = new ArrayList<>();
    @JsonProperty
    private Long ping;
    @JsonProperty
    private String error;

    public ClusterNodeInfo() {
    }

    public ClusterNodeInfo(final long discoverTime,
                           final BuildInfo buildInfo,
                           final String nodeName,
                           final String endpointUrl) {
        this.discoverTime = discoverTime;
        this.buildInfo = buildInfo;
        this.nodeName = nodeName;
        this.endpointUrl = endpointUrl;
    }

    @JsonCreator
    public ClusterNodeInfo(@JsonProperty("discoverTime") final long discoverTime,
                           @JsonProperty("buildInfo") final BuildInfo buildInfo,
                           @JsonProperty("nodeName") final String nodeName,
                           @JsonProperty("endpointUrl") final String endpointUrl,
                           @JsonProperty("itemList") final List<ClusterNodeInfoItem> itemList,
                           @JsonProperty("ping") final Long ping,
                           @JsonProperty("error") final String error) {
        this.discoverTime = discoverTime;
        this.buildInfo = buildInfo;
        this.nodeName = nodeName;
        this.endpointUrl = endpointUrl;
        this.itemList = itemList;
        this.ping = ping;
        this.error = error;
    }

    public void addItem(final String nodeName, final boolean active, final boolean master) {
        if (itemList == null) {
            itemList = new ArrayList<>();
        }
        itemList.add(new ClusterNodeInfoItem(nodeName, active, master));
    }

    public long getDiscoverTime() {
        return discoverTime;
    }

    public void setDiscoverTime(final long discoverTime) {
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

    public String getEndpointUrl() {
        return endpointUrl;
    }

    public void setEndpointUrl(final String endpointUrl) {
        this.endpointUrl = endpointUrl;
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

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ClusterNodeInfo that = (ClusterNodeInfo) o;
        return Objects.equals(discoverTime, that.discoverTime) &&
                Objects.equals(buildInfo, that.buildInfo) &&
                Objects.equals(nodeName, that.nodeName) &&
                Objects.equals(endpointUrl, that.endpointUrl) &&
                Objects.equals(itemList, that.itemList) &&
                Objects.equals(ping, that.ping) &&
                Objects.equals(error, that.error);
    }

    @Override
    public String toString() {
        return "ClusterNodeInfo{" +
                "discoverTime='" + discoverTime + '\'' +
                ", buildInfo=" + buildInfo +
                ", nodeName='" + nodeName + '\'' +
                ", endpointUrl='" + endpointUrl + '\'' +
                ", itemList=" + itemList +
                ", ping=" + ping +
                ", error='" + error + '\'' +
                '}';
    }

    @Override
    public int hashCode() {
        return Objects.hash(discoverTime, buildInfo, nodeName, endpointUrl, itemList, ping, error);
    }

    @JsonInclude(Include.NON_NULL)
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

        @Override
        public boolean equals(final Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            final ClusterNodeInfoItem that = (ClusterNodeInfoItem) o;
            return active == that.active &&
                    master == that.master &&
                    Objects.equals(nodeName, that.nodeName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(nodeName, active, master);
        }

        @Override
        public String toString() {
            return "ClusterNodeInfoItem{" +
                    "nodeName='" + nodeName + '\'' +
                    ", active=" + active +
                    ", master=" + master +
                    '}';
        }
    }
}
