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

import stroom.docref.DocRef;
import stroom.explorer.shared.TabSession;
import stroom.security.api.SecurityContext;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Objects;

@Singleton
public class TabSessionServiceImpl implements TabSessionService {

    private final SecurityContext securityContext;
    private final TabSessionDao tabSessionDao;

    @Inject
    public TabSessionServiceImpl(final SecurityContext securityContext,
                                 final TabSessionDao tabSessionDao) {
        this.securityContext = securityContext;
        this.tabSessionDao = tabSessionDao;
    }

    @Override
    public List<TabSession> addForCurrentUser(final String name, final List<DocRef> docRefs) {
        final String userId = getCurrentUser().getUuid();

        tabSessionDao.deleteTabSession(userId, name);
        tabSessionDao.createTabSession(userId, name, docRefs);

        return tabSessionDao.getTabSessionForUser(userId);
    }

    @Override
    public List<TabSession> getForCurrentUser() {
        return tabSessionDao.getTabSessionForUser(getCurrentUser().getUuid());
    }

    @Override
    public List<TabSession> deleteForCurrentUser(final String name) {
        final String userId = getCurrentUser().getUuid();

        tabSessionDao.deleteTabSession(userId, name);

        return tabSessionDao.getTabSessionForUser(userId);
    }

    private UserRef getCurrentUser() {
        final UserRef userRef = securityContext.getUserRef();
        Objects.requireNonNull(userRef, "No logged in user");
        return userRef;
    }
}
