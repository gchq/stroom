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

import stroom.dispatch.shared.Action;
import stroom.entity.shared.BaseCriteria.OrderByDirection;
import stroom.entity.shared.OrderBy;
import stroom.entity.shared.ResultList;

/**
 * Action handler
 */
public class FindSystemTableStatusAction extends Action<ResultList<DBTableStatus>> {
    private static final long serialVersionUID = 1019772059356717579L;

    private OrderBy orderBy;
    private OrderByDirection orderByDirection;

    public FindSystemTableStatusAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public OrderBy getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(final OrderBy orderBy) {
        this.orderBy = orderBy;
    }

    public OrderByDirection getOrderByDirection() {
        return orderByDirection;
    }

    public void setOrderByDirection(final OrderByDirection orderByDirection) {
        this.orderByDirection = orderByDirection;
    }

    @Override
    public String getTaskName() {
        return "Find Table Status";
    }
}
