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

package stroom.entity.shared;

import stroom.dispatch.shared.Action;
import stroom.util.shared.SharedObject;

public class FindAction<C extends BaseCriteria, E extends SharedObject> extends Action<ResultList<E>>
        implements HasCriteria<C> {
    private static final long serialVersionUID = 800905016214418723L;

    private C criteria;

    public FindAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public FindAction(final C criteria) {
        this.criteria = criteria;
    }

    public C getCriteria() {
        return criteria;
    }

    public void setCriteria(C criteria) {
        this.criteria = criteria;
    }

    @Override
    public String getTaskName() {
        return "Find: " + criteria;
    }
}
