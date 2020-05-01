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

package stroom.data.store.impl.fs.shared;

import stroom.data.store.impl.fs.shared.FsVolume.VolumeUseStatus;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.PageRequest;
import stroom.util.shared.Selection;
import stroom.util.shared.Sort;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FindFsVolumeCriteria extends BaseCriteria {
    public static final String FIELD_ID = "Id";

    @JsonProperty
    private final Selection<VolumeUseStatus> selection;

    public static FindFsVolumeCriteria matchAll() {
        return new FindFsVolumeCriteria(
                null,
                null,
                Selection.selectAll());
    }

    public static FindFsVolumeCriteria matchNone() {
        return new FindFsVolumeCriteria(
                null,
                null,
                Selection.selectNone());
    }

    @JsonCreator
    public FindFsVolumeCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                @JsonProperty("sortList") final List<Sort> sortList,
                                @JsonProperty("selection") final Selection<VolumeUseStatus> selection) {
        super(pageRequest, sortList);
        this.selection = selection;
    }

    public Selection<VolumeUseStatus> getSelection() {
        return selection;
    }
}
