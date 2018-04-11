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

package stroom.streamstore;

import stroom.entity.shared.BaseCriteria;
import stroom.entity.shared.CriteriaSet;
import stroom.entity.shared.EntityIdSet;
import stroom.node.shared.Node;
import stroom.node.shared.Volume;
import stroom.streamstore.shared.Stream;
import stroom.streamstore.shared.StreamStatus;

public class FindStreamVolumeCriteria extends BaseCriteria {
    private static final long serialVersionUID = 3528656425356870590L;

    private StreamRange streamRange;

    private CriteriaSet<StreamStatus> streamStatusSet = null;
    private EntityIdSet<Node> nodeIdSet = null;
    private EntityIdSet<Volume> volumeIdSet = null;
    private EntityIdSet<Stream> streamIdSet = null;

    public static FindStreamVolumeCriteria create(final Stream stream) {
        FindStreamVolumeCriteria rtn = new FindStreamVolumeCriteria();
        rtn.obtainStreamIdSet().add(stream);
        return rtn;
    }

    public boolean isValidCriteria() {
        if (streamIdSet != null && streamIdSet.isConstrained()) {
            return true;
        }
        return streamRange != null && streamRange.isFileLocation();
    }

    public StreamRange getStreamRange() {
        return streamRange;
    }

    public void setStreamRange(StreamRange streamRange) {
        this.streamRange = streamRange;
    }

    public EntityIdSet<Node> getNodeIdSet() {
        return nodeIdSet;
    }

    public EntityIdSet<Node> obtainNodeIdSet() {
        if (nodeIdSet == null) {
            nodeIdSet = new EntityIdSet<>();
        }
        return nodeIdSet;
    }

    public EntityIdSet<Stream> getStreamIdSet() {
        return streamIdSet;
    }

    public EntityIdSet<Stream> obtainStreamIdSet() {
        if (streamIdSet == null) {
            streamIdSet = new EntityIdSet<>();
        }
        return streamIdSet;
    }

    public EntityIdSet<Volume> getVolumeIdSet() {
        return volumeIdSet;
    }

    public EntityIdSet<Volume> obtainVolumeIdSet() {
        if (volumeIdSet == null) {
            volumeIdSet = new EntityIdSet<>();
        }
        return volumeIdSet;
    }

    public CriteriaSet<StreamStatus> getStreamStatusSet() {
        return streamStatusSet;
    }

    public CriteriaSet<StreamStatus> obtainStreamStatusSet() {
        if (streamStatusSet == null) {
            streamStatusSet = new CriteriaSet<>();
        }
        return streamStatusSet;
    }

}
