/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.dispatch.client.RestFactory;
import stroom.docref.DocRef;
import stroom.query.api.datasource.QueryField;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.SuggestionsResource;
import stroom.task.client.TaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.Timer;
import com.google.gwt.user.client.ui.MultiWordSuggestOracle.MultiWordSuggestion;
import com.google.gwt.user.client.ui.SuggestOracle;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AsyncSuggestOracle extends SuggestOracle {

    private static final SuggestionsResource SUGGESTIONS_RESOURCE = GWT.create(SuggestionsResource.class);
    private static final int DEBOUNCE_PERIOD_MILLIS = 400;

    private static final Map<FetchSuggestionsRequest, List<String>> CACHE = new HashMap<>();

    private RestFactory restFactory;
    private DocRef dataSource;
    private QueryField field;
    private Timer requestTimer;
    private final TaskMonitorFactory taskMonitorFactory;

    public AsyncSuggestOracle(final TaskMonitorFactory taskMonitorFactory) {
        this.taskMonitorFactory = taskMonitorFactory;
    }

    public void setRestFactory(final RestFactory restFactory) {
        this.restFactory = restFactory;
    }

    public void setDataSource(final DocRef dataSource) {
        this.dataSource = dataSource;
    }

    public void setField(final QueryField field) {
        this.field = field;
    }

    @Override
    public void requestDefaultSuggestions(final Request request, final Callback callback) {
        requestSuggestions(request, callback, 0);
    }

    @Override
    public void requestSuggestions(final Request request, final Callback callback) {
        requestSuggestions(request, callback, DEBOUNCE_PERIOD_MILLIS);
    }

    private void requestSuggestions(final Request request,
                                    final Callback callback,
                                    final int debouncePeriod) {
        if (restFactory != null && dataSource != null) {
            // Debounce requests so we don't spam the backend
            if (requestTimer != null) {
                requestTimer.cancel();
            }

            requestTimer = new Timer() {
                @Override
                public void run() {
                    final FetchSuggestionsRequest fetchSuggestionsRequest =
                            new FetchSuggestionsRequest(dataSource, field, request.getQuery());
                    final List<String> cachedSuggestions = CACHE.get(fetchSuggestionsRequest);
                    if (cachedSuggestions != null) {
                        returnSuggestions(request, callback, cachedSuggestions);

                    } else {
                        restFactory
                                .create(SUGGESTIONS_RESOURCE)
                                .method(res -> res.fetch(fetchSuggestionsRequest))
                                .onSuccess(result -> {
                                    if (result.isCacheable()) {
                                        CACHE.put(fetchSuggestionsRequest, result.getList());
                                    }

                                    returnSuggestions(request, callback, result.getList());
                                })
                                .taskMonitorFactory(taskMonitorFactory)
                                .exec();
                    }
                }
            };

            requestTimer.schedule(debouncePeriod);
        }
    }

    private void returnSuggestions(final Request request,
                                   final Callback callback,
                                   final List<String> list) {
        final List<Suggestion> suggestions = new ArrayList<>();
        for (final String string : list) {
            suggestions.add(new MultiWordSuggestion(string, string));
        }
        callback.onSuggestionsReady(request, new Response(suggestions));
    }
}
