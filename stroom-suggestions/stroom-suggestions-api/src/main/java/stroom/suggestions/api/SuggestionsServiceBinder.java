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

package stroom.suggestions.api;

import com.google.inject.Binder;
import com.google.inject.multibindings.MapBinder;

public final class SuggestionsServiceBinder {

    private final MapBinder<String, SuggestionsQueryHandler> mapBinder;

    private SuggestionsServiceBinder(final Binder binder) {
        mapBinder = MapBinder.newMapBinder(binder, String.class, SuggestionsQueryHandler.class);
    }

    public static SuggestionsServiceBinder create(final Binder binder) {
        return new SuggestionsServiceBinder(binder);
    }

    public <T extends SuggestionsQueryHandler> SuggestionsServiceBinder bind(
            final String dataSourceType,
            final Class<T> queryHandlerClass
    ) {
        mapBinder.addBinding(dataSourceType).to(queryHandlerClass);
        return this;
    }
}
