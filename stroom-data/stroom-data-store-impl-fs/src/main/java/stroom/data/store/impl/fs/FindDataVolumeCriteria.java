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

package stroom.data.store.impl.fs;

import stroom.meta.shared.Meta;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Selection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FindDataVolumeCriteria extends BaseCriteria {

    @JsonProperty
    private Selection<Integer> volumeIdSet;
    @JsonProperty
    private Selection<Long> metaIdSet;

    public static FindDataVolumeCriteria matchAll() {
        return new FindDataVolumeCriteria(
                null,
                null,
                Selection.selectAll(),
                Selection.selectAll());
    }

    public static FindDataVolumeCriteria matchNone() {
        return new FindDataVolumeCriteria(
                null,
                null,
                Selection.selectNone(),
                Selection.selectNone());
    }

    @JsonCreator
    public FindDataVolumeCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                  @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                  @JsonProperty("volumeIdSet") final Selection<Integer> volumeIdSet,
                                  @JsonProperty("metaIdSet") final Selection<Long> metaIdSet) {
        super(pageRequest, sortList);
        this.volumeIdSet = volumeIdSet;
        this.metaIdSet = metaIdSet;
    }

    public static FindDataVolumeCriteria create(final Meta meta) {
        final FindDataVolumeCriteria rtn = FindDataVolumeCriteria.matchAll();
        rtn.obtainMetaIdSet().add(meta.getId());
        return rtn;
    }

    @JsonIgnore
    public boolean isValidCriteria() {
        return metaIdSet != null && !metaIdSet.isMatchAll() && metaIdSet.size() > 0;
    }

    public Selection<Long> getMetaIdSet() {
        return metaIdSet;
    }

    public Selection<Long> obtainMetaIdSet() {
        if (metaIdSet == null) {
            metaIdSet = Selection.selectAll();
        }
        return metaIdSet;
    }

    public Selection<Integer> getVolumeIdSet() {
        return volumeIdSet;
    }

    public Selection<Integer> obtainVolumeIdSet() {
        if (volumeIdSet == null) {
            volumeIdSet = Selection.selectAll();
        }
        return volumeIdSet;
    }
}
