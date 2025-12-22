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

package stroom.suggestions.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.query.shared.FetchSuggestionsRequest;
import stroom.query.shared.Suggestions;
import stroom.query.shared.SuggestionsResource;
import stroom.suggestions.api.SuggestionsService;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

@AutoLogged(OperationType.UNLOGGED)
class SuggestionsResourceImpl implements SuggestionsResource {

    private final Provider<SuggestionsService> suggestionsServiceProvider;

    @Inject
    SuggestionsResourceImpl(final Provider<SuggestionsService> suggestionsServiceProvider) {
        this.suggestionsServiceProvider = suggestionsServiceProvider;
    }

    @Override
    public Suggestions fetch(final FetchSuggestionsRequest request) {

        // Pretty sure we don't want auditing on this given that it gets
        // hit on each keystroke
        return suggestionsServiceProvider.get().fetch(request);
    }

}
