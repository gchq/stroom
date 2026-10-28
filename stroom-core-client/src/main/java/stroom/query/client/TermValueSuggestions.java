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

import stroom.docref.DocRef;
import stroom.item.client.SuggestionSource;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.Suggestions;

import com.google.gwt.user.client.Timer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/// Suggests a term's values for a [stroom.item.client.SuggestionBox]: True and False for a boolean
/// field, else what its data source suggests for the field, fetched (by whatever [#setFetcher] is
/// given, e.g. a term editor's controller) once typing pauses, and kept if the source says it may be.
/// (It was a GWT `SuggestOracle` for GWT's `SuggestBox`.)
public class TermValueSuggestions implements SuggestionSource {

    private static final int DEBOUNCE_PERIOD_MILLIS = 400;

    private static final Map<FetchSuggestionsRequest, List<String>> CACHE = new HashMap<>();

    private BiConsumer<FetchSuggestionsRequest, Consumer<Suggestions>> fetcher;
    private DocRef dataSource;
    private QueryField field;
    private Timer requestTimer;
    // The text last asked about: asking again (a click, Down) fetches at once, while typing waits
    private String lastText;

    /// @param fetcher Fetches a request's suggestions and gives them to the consumer, or null to
    ///                suggest nothing but a boolean's values.
    public void setFetcher(final BiConsumer<FetchSuggestionsRequest, Consumer<Suggestions>> fetcher) {
        this.fetcher = fetcher;
    }

    /// @param dataSource The data source whose values are suggested.
    public void setDataSource(final DocRef dataSource) {
        this.dataSource = dataSource;
    }

    /// @param field The field whose values are suggested.
    public void setField(final QueryField field) {
        this.field = field;
    }

    @Override
    public void fetch(final String text, final Consumer<List<String>> consumer) {
        if (field != null && FieldType.BOOLEAN.equals(field.getFldType())) {
            consumer.accept(booleanSuggestions(text));
            return;
        }
        if (fetcher == null || dataSource == null) {
            return;
        }
        // Debounce typing so we don't spam the backend
        final int debouncePeriod = Objects.equals(text, lastText)
                ? 0
                : DEBOUNCE_PERIOD_MILLIS;
        lastText = text;
        cancel();
        requestTimer = new Timer() {
            @Override
            public void run() {
                final FetchSuggestionsRequest request = new FetchSuggestionsRequest(dataSource, field, text);
                final List<String> cached = CACHE.get(request);
                if (cached != null) {
                    consumer.accept(cached);
                } else {
                    fetcher.accept(request, result -> {
                        if (result.isCacheable()) {
                            CACHE.put(request, result.getList());
                        }
                        consumer.accept(result.getList());
                    });
                }
            }
        };
        requestTimer.schedule(debouncePeriod);
    }

    @Override
    public void cancel() {
        if (requestTimer != null) {
            requestTimer.cancel();
            requestTimer = null;
        }
    }

    /// @param text What has been typed.
    /// @return True and False, as far as they start with what has been typed (ignoring case).
    static List<String> booleanSuggestions(final String text) {
        final String typed = text == null
                ? ""
                : text.trim().toLowerCase();
        final List<String> list = new ArrayList<>();
        if ("true".startsWith(typed)) {
            list.add("True");
        }
        if ("false".startsWith(typed)) {
            list.add("False");
        }
        return list;
    }
}
