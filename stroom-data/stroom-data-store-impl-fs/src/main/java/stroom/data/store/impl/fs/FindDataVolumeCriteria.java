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

package stroom.data.store.impl.fs;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.meta.shared.Meta;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaSet;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Sort;

import java.util.List;

@JsonInclude(Include.NON_DEFAULT)
public class FindDataVolumeCriteria extends BaseCriteria {
    @JsonProperty
    private CriteriaSet<Integer> volumeIdSet;
    @JsonProperty
    private CriteriaSet<Long> metaIdSet;

    public FindDataVolumeCriteria() {
    }

    @JsonCreator
    public FindDataVolumeCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                  @JsonProperty("sortList") final List<Sort> sortList,
                                  @JsonProperty("volumeIdSet") final CriteriaSet<Integer> volumeIdSet,
                                  @JsonProperty("metaIdSet") final CriteriaSet<Long> metaIdSet) {
        super(pageRequest, sortList);
        this.volumeIdSet = volumeIdSet;
        this.metaIdSet = metaIdSet;
    }

    public static FindDataVolumeCriteria create(final Meta meta) {
        FindDataVolumeCriteria rtn = new FindDataVolumeCriteria();
        rtn.obtainMetaIdSet().add(meta.getId());
        return rtn;
    }

    public boolean isValidCriteria() {
        if (metaIdSet != null && metaIdSet.isConstrained()) {
            return true;
        }
        return false;
//        return streamRange != null && streamRange.isFileLocation();
    }

//    public StreamRange getStreamRange() {
//        return streamRange;
//    }
//
//    public void setStreamRange(StreamRange streamRange) {
//        this.streamRange = streamRange;
//    }
//
//    public CriteriaSet<Long> getNodeIdSet() {
//        return nodeIdSet;
//    }
//
//    public CriteriaSet<Long> obtainNodeIdSet() {
//        if (nodeIdSet == null) {
//            nodeIdSet = new CriteriaSet<>();
//        }
//        return nodeIdSet;
//    }

    public CriteriaSet<Long> getMetaIdSet() {
        return metaIdSet;
    }

    public CriteriaSet<Long> obtainMetaIdSet() {
        if (metaIdSet == null) {
            metaIdSet = new CriteriaSet<>();
        }
        return metaIdSet;
    }

    public CriteriaSet<Integer> getVolumeIdSet() {
        return volumeIdSet;
    }

    public CriteriaSet<Integer> obtainVolumeIdSet() {
        if (volumeIdSet == null) {
            volumeIdSet = new CriteriaSet<>();
        }
        return volumeIdSet;
    }

//    public CriteriaSet<StreamStatus> getStreamStatusSet() {
//        return streamStatusSet;
//    }
//
//    public CriteriaSet<StreamStatus> obtainStreamStatusSet() {
//        if (streamStatusSet == null) {
//            streamStatusSet = new CriteriaSet<>();
//        }
//        return streamStatusSet;
//    }
}
