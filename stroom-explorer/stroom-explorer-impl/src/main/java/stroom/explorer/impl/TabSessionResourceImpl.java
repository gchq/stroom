/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.explorer.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.explorer.shared.TabSession;
import stroom.explorer.shared.TabSessionAddRequest;
import stroom.explorer.shared.TabSessionDeleteRequest;
import stroom.explorer.shared.TabSessionResource;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

@AutoLogged(OperationType.MANUALLY_LOGGED)
public class TabSessionResourceImpl implements TabSessionResource {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TabSessionResourceImpl.class);

    final Provider<TabSessionService> tabSessionServiceProvider;

    @Inject
    public TabSessionResourceImpl(final Provider<TabSessionService> tabSessionServiceProvider) {
        this.tabSessionServiceProvider = tabSessionServiceProvider;
    }

    @Override
    public List<TabSession> getForCurrentUser() {
        try {
            return tabSessionServiceProvider.get().getForCurrentUser();
        } catch (final Exception ex) {
            LOGGER.error("Unexpected problem occurred getting user tab sessions", ex);
            throw new RuntimeException("An unexpected problem has occurred.");
        }
    }

    @Override
    public List<TabSession> add(final TabSessionAddRequest request) {
        try {
            return tabSessionServiceProvider.get().addForCurrentUser(request.getName(), request.getDocRefs());
        } catch (final Exception ex) {
            LOGGER.error("Unexpected problem occurred adding tab session " + request, ex);
            throw new RuntimeException("An unexpected problem has occurred.");
        }
    }

    @Override
    public List<TabSession> delete(final TabSessionDeleteRequest request) {
        try {
            return tabSessionServiceProvider.get().deleteForCurrentUser(request.getName());
        } catch (final Exception ex) {
            LOGGER.error("Unexpected problem occurred deleting tab session " + request, ex);
            throw new RuntimeException("An unexpected problem has occurred.");
        }
    }
}
