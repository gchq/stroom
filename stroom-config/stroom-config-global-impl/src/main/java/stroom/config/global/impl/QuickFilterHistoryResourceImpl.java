/*
 * Copyright 2026 Crown Copyright
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

package stroom.config.global.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.quickfilter.shared.QuickFilterHistoryKey;
import stroom.quickfilter.shared.QuickFilterHistoryResource;
import stroom.quickfilter.shared.QuickFilterHistoryService;
import stroom.quickfilter.shared.RecordQuickFilterUseRequest;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.BadRequestException;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Nothing here is audit logged: this is the caller's own UI state, and the searches these
 * filters drive are what get logged.
 */
@AutoLogged(OperationType.UNLOGGED)
public class QuickFilterHistoryResourceImpl implements QuickFilterHistoryResource {

    // The context is stored, so it is bounded and constrained rather than accepted verbatim. The
    // server does not otherwise know the set of valid contexts; it does not need to.
    private static final Pattern CONTEXT_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9_-]{1," + QuickFilterHistoryKey.MAX_CONTEXT_LENGTH + "}$");
    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private final Provider<QuickFilterHistoryService> serviceProvider;

    @Inject
    QuickFilterHistoryResourceImpl(final Provider<QuickFilterHistoryService> serviceProvider) {
        this.serviceProvider = serviceProvider;
    }

    @Override
    public List<String> fetch(final QuickFilterHistoryKey key) {
        return serviceProvider.get().fetch(validate(key));
    }

    @Override
    public void record(final RecordQuickFilterUseRequest request) {
        Objects.requireNonNull(request, "request");
        serviceProvider.get().recordUse(validate(request.getKey()), request.getFilterText());
    }

    @Override
    public void clear(final QuickFilterHistoryKey key) {
        serviceProvider.get().clear(validate(key));
    }

    static QuickFilterHistoryKey validate(final QuickFilterHistoryKey key) {
        if (key == null) {
            throw new BadRequestException("key is required");
        }
        if (!CONTEXT_PATTERN.matcher(key.getContext()).matches()) {
            throw new BadRequestException("Invalid quick filter context '" + key.getContext() + "'");
        }
        final String dataSourceUuid = key.getDataSourceUuid();
        if (!dataSourceUuid.isEmpty() && !UUID_PATTERN.matcher(dataSourceUuid).matches()) {
            throw new BadRequestException("Invalid data source uuid '" + dataSourceUuid + "'");
        }
        return key;
    }
}
