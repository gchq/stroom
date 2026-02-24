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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Singleton
public class TabSessionServiceImpl implements TabSessionService {

    final Map<String, List<TabSession>> savedSessions = new HashMap<>();

    private final SecurityContext securityContext;

    @Inject
    public TabSessionServiceImpl(final SecurityContext securityContext) {
        this.securityContext = securityContext;
    }

    @Override
    public List<TabSession> add(final String sessionId, final String name, final List<DocRef> docRefs) {
        final String userId = getCurrentUser().getUuid();

        final List<TabSession> userSessions = savedSessions.computeIfAbsent(userId, id -> new ArrayList<>());

        userSessions.removeIf(session -> Objects.equals(name, session.getName()));

        final TabSession newTabSession = new TabSession(userId, sessionId, name, docRefs);
        userSessions.add(newTabSession);

        return userSessions;
    }

    @Override
    public List<TabSession> getForCurrentUser() {
        return savedSessions.get(getCurrentUser().getUuid());
    }

    @Override
    public List<TabSession> delete(final String sessionId) {
        final String userId = getCurrentUser().getUuid();

        final List<TabSession> userSessions = savedSessions.get(userId);

        if (userSessions == null) {
            return List.of();
        }

        userSessions.removeIf(session -> Objects.equals(session.getSessionId(), sessionId));

        return userSessions;
    }

    private UserRef getCurrentUser() {
        final UserRef userRef = securityContext.getUserRef();
        Objects.requireNonNull(userRef, "No logged in user");
        return userRef;
    }
}
