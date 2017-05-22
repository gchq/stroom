/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.dashboard.shared;

import stroom.entity.shared.Action;
import stroom.query.api.v1.DocRef;

public class FetchDataSourceFieldsAction extends Action<DataSourceFields> {
    private static final long serialVersionUID = -6668626615097471925L;

    private DocRef dataSourceRef;

    public FetchDataSourceFieldsAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public FetchDataSourceFieldsAction(final DocRef dataSourceRef) {
        this.dataSourceRef = dataSourceRef;
    }

    public DocRef getDataSourceRef() {
        return dataSourceRef;
    }

    @Override
    public String getTaskName() {
        return "Fetch Data Source Fields";
    }
}
