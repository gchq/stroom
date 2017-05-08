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

import stroom.util.shared.SharedObject;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class ClusterNodeInfo implements SharedObject {
    private static final long serialVersionUID = -15041191801817241L;

    private String discoverTime;
    private String buildDate;
    private String buildVersion;
    private String upDate;
    private String nodeName;
    private String clusterURL;
    private List<ClusterNodeInfoItem> itemList = new ArrayList<ClusterNodeInfoItem>();

    public ClusterNodeInfo() {
        // Default constructor necessary for GWT serialisation.
    }

    public ClusterNodeInfo(final String discoverTime, final String buildDate, final String buildVersion,
            final String upDate, final String nodeName, final String clusterURL) {
        this.discoverTime = discoverTime;
        this.buildDate = buildDate;
        this.buildVersion = buildVersion;
        this.upDate = upDate;
        this.nodeName = nodeName;
        this.clusterURL = clusterURL;
    }

    public void addItem(final Node node, final boolean active, final boolean master) {
        final ClusterNodeInfoItem clusterNodeInfoItem = new ClusterNodeInfoItem();
        clusterNodeInfoItem.setNode(node);
        clusterNodeInfoItem.setActive(active);
        clusterNodeInfoItem.setMaster(master);
        itemList.add(clusterNodeInfoItem);
    }

    public String getDiscoverTime() {
        return discoverTime;
    }

    public String getBuildDate() {
        return buildDate;
    }

    public String getBuildVersion() {
        return buildVersion;
    }

    public String getUpDate() {
        return upDate;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getClusterURL() {
        return clusterURL;
    }

    public List<ClusterNodeInfoItem> getItemList() {
        return itemList;
    }

    public static class ClusterNodeInfoItem implements Serializable {
        private static final long serialVersionUID = -8555764783069283678L;
        private Node node;
        private boolean active;
        private boolean master;

        public Node getNode() {
            return node;
        }

        public void setNode(final Node node) {
            this.node = node;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(final boolean active) {
            this.active = active;
        }

        public boolean isMaster() {
            return master;
        }

        public void setMaster(final boolean master) {
            this.master = master;
        }
    }
}
