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

import stroom.datasource.api.v2.AbstractField;
import stroom.docref.DocRef;

public class FetchSuggestionsRequest {
    private DocRef dataSource;
    private AbstractField field;
    private String text;

    public FetchSuggestionsRequest() {
        // Default constructor necessary for GWT serialisation.
    }

    public FetchSuggestionsRequest(final DocRef dataSource, final AbstractField field, final String text) {
        this.dataSource = dataSource;
        this.field = field;
        this.text = text;
    }

    public DocRef getDataSource() {
        return dataSource;
    }

    public void setDataSource(final DocRef dataSource) {
        this.dataSource = dataSource;
    }

    public AbstractField getField() {
        return field;
    }

    public void setField(final AbstractField field) {
        this.field = field;
    }

    public String getText() {
        return text;
    }

    public void setText(final String text) {
        this.text = text;
    }
}
