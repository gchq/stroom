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

package stroom.streamtask.shared;

import stroom.entity.shared.Action;
import stroom.data.meta.api.FindDataCriteria;
import stroom.streamstore.shared.ReprocessDataInfo;
import stroom.util.shared.SharedList;

public class ReprocessDataAction extends Action<SharedList<ReprocessDataInfo>> {
    private static final long serialVersionUID = -3560107233301674555L;

    private FindDataCriteria criteria;

    public ReprocessDataAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public ReprocessDataAction(final FindDataCriteria criteria) {
        this.criteria = criteria;
    }

    public FindDataCriteria getCriteria() {
        return criteria;
    }

    public void setCriteria(FindDataCriteria criteria) {
        this.criteria = criteria;
    }

    @Override
    public String getTaskName() {
        return "Reprocess";
    }
}
