/*
 * Copyright 2017 Crown Copyright
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
 *
 */

package stroom.dashboard.shared;

import stroom.entity.shared.Action;

public class CreateQueryFavouriteAction extends Action<QueryEntity> {
    private static final long serialVersionUID = 800905016214418723L;

    private QueryEntity queryEntity;

    public CreateQueryFavouriteAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public CreateQueryFavouriteAction(final QueryEntity queryEntity) {
        this.queryEntity = queryEntity;
    }

    public QueryEntity getQueryEntity() {
        return queryEntity;
    }

    @Override
    public String getTaskName() {
        return "Create query favourite";
    }
}
