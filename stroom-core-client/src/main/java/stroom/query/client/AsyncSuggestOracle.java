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

package stroom.query.client;

import com.google.gwt.user.client.ui.MultiWordSuggestOracle.MultiWordSuggestion;
import com.google.gwt.user.client.ui.SuggestOracle;
import stroom.datasource.api.v2.DataSourceField;
import stroom.dispatch.client.ClientDispatchAsync;
import stroom.query.api.v2.DocRef;
import stroom.query.shared.FetchSuggestionsAction;
import stroom.util.shared.SharedString;

import java.util.ArrayList;
import java.util.List;

public class AsyncSuggestOracle extends SuggestOracle {
    private ClientDispatchAsync dispatcher;
    private DocRef dataSource;
    private DataSourceField field;

    public void setDispatcher(final ClientDispatchAsync dispatcher) {
        this.dispatcher = dispatcher;
    }

    public void setDataSource(final DocRef dataSource) {
        this.dataSource = dataSource;
    }

    public void setField(final DataSourceField field) {
        this.field = field;
    }

    @Override
    public void requestSuggestions(final Request request, final Callback callback) {
        if (dispatcher != null && dataSource != null) {
            dispatcher.exec(new FetchSuggestionsAction(dataSource, field, request.getQuery())).onSuccess(result -> {
                final List<Suggestion> suggestions = new ArrayList<>();
                for (final SharedString string : result) {
                    suggestions.add(new MultiWordSuggestion(string.toString(), string.toString()));
                }
                callback.onSuggestionsReady(request, new Response(suggestions));
            });
        }
    }
}