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

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.MultiWordSuggestOracle.MultiWordSuggestion;
import com.google.gwt.user.client.ui.SuggestOracle;
import stroom.datasource.api.v2.AbstractField;
import stroom.dispatch.client.Rest;
import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.SuggestionsResource;

import java.util.ArrayList;
import java.util.List;

public class AsyncSuggestOracle extends SuggestOracle {
    private static final SuggestionsResource SUGGESTIONS_RESOURCE = GWT.create(SuggestionsResource.class);

    private RestFactory restFactory;
    private DocRef dataSource;
    private AbstractField field;

    public void setRestFactory(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void setDataSource(final DocRef dataSource) {
        this.dataSource = dataSource;
    }

    public void setField(final AbstractField field) {
        this.field = field;
    }

    @Override
    public void requestDefaultSuggestions(final Request request, final Callback callback) {
        requestSuggestions(request, callback);
    }

    @Override
    public void requestSuggestions(final Request request, final Callback callback) {
        if (restFactory != null && dataSource != null) {
            final Rest<List<String>> rest = restFactory.create();
            rest
                    .onSuccess(result -> {
                        final List<Suggestion> suggestions = new ArrayList<>();
                        for (final String string : result) {
                            suggestions.add(new MultiWordSuggestion(string, string));
                        }
                        callback.onSuggestionsReady(request, new Response(suggestions));
                    })
                    .call(SUGGESTIONS_RESOURCE)
                    .fetch(new FetchSuggestionsRequest(dataSource, field, request.getQuery()));
        }
    }
}