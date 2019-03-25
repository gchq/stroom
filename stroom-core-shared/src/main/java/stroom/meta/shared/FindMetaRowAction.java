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

package stroom.meta.shared;

import stroom.task.shared.Action;
import stroom.util.shared.HasCriteria;
import stroom.util.shared.ResultList;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.MetaRow;

public class FindMetaRowAction extends Action<ResultList<MetaRow>> implements HasCriteria<FindMetaCriteria> {
    private static final long serialVersionUID = -3560107233301674555L;

    private FindMetaCriteria criteria;

    public FindMetaRowAction() {
    }

    public FindMetaRowAction(final FindMetaCriteria criteria) {
        this.criteria = criteria;
    }

    public FindMetaCriteria getCriteria() {
        return criteria;
    }

    public void setCriteria(FindMetaCriteria criteria) {
        this.criteria = criteria;
    }

    @Override
    public String getTaskName() {
        return "Find Stream Action";
    }
}
