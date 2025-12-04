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

package stroom.data.store.impl.fs.shared;

import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Selection;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FindFsVolumeCriteria extends BaseCriteria {

    public static final String FIELD_ID = "Id";

    @JsonProperty
    private final FsVolumeGroup group;
    @JsonProperty
    private final Selection<VolumeUseStatus> selection;

    public static FindFsVolumeCriteria matchAll() {
        return new FindFsVolumeCriteria(
                null,
                null,
                null,
                Selection.selectAll());
    }

    public static FindFsVolumeCriteria matchNone() {
        return new FindFsVolumeCriteria(
                null,
                null,
                null,
                Selection.selectNone());
    }

    @JsonCreator
    public FindFsVolumeCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                @JsonProperty("group")  final FsVolumeGroup group,
                                @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                @JsonProperty("selection") final Selection<VolumeUseStatus> selection) {
        super(pageRequest, sortList);
        this.group = group;
        this.selection = selection;
    }

    public FsVolumeGroup getGroup() {
        return group;
    }

    public Selection<VolumeUseStatus> getSelection() {
        return selection;
    }
}
