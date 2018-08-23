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
 */

package stroom.query.shared;

import stroom.datasource.api.v2.DataSourceField;
import stroom.task.shared.Action;
import stroom.docref.DocRef;
import stroom.util.shared.SharedList;
import stroom.util.shared.SharedString;

public class FetchSuggestionsAction extends Action<SharedList<SharedString>> {
    private static final long serialVersionUID = -7883596658097683550L;

    private DocRef dataSource;
    private DataSourceField field;
    private String text;

    public FetchSuggestionsAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public FetchSuggestionsAction(final DocRef dataSource, final DataSourceField field, final String text) {
        this.dataSource = dataSource;
        this.field = field;
        this.text = text;
    }

    public DocRef getDataSource() {
        return dataSource;
    }

    public DataSourceField getField() {
        return field;
    }

    public String getText() {
        return text;
    }

    @Override
    public String getTaskName() {
        return "Fetch Field Suggestions";
    }
}
