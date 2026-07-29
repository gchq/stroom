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

package stroom.security.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.shared.FindUserAccessCriteria;
import stroom.security.shared.SessionDetails;
import stroom.security.shared.UserAccessResource;
import stroom.security.shared.UserAccessRow;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

/**
 * @see UserAccessResource
 */
@AutoLogged
class UserAccessResourceImpl implements UserAccessResource {

    private final Provider<UserAccessService> userAccessServiceProvider;
    private final Provider<UserAccessRevocationService> revocationServiceProvider;

    @Inject
    UserAccessResourceImpl(final Provider<UserAccessService> userAccessServiceProvider,
                           final Provider<UserAccessRevocationService> revocationServiceProvider) {
        this.userAccessServiceProvider = userAccessServiceProvider;
        this.revocationServiceProvider = revocationServiceProvider;
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public ResultPage<UserAccessRow> find(final FindUserAccessCriteria criteria) {
        return userAccessServiceProvider.get().find(criteria);
    }

    @Override
    @AutoLogged(OperationType.VIEW)
    public List<SessionDetails> listSessions(final String subjectId) {
        return userAccessServiceProvider.get().listSessions(subjectId);
    }

    @Override
    @AutoLogged(OperationType.DELETE)
    public Integer revokeAccess(final String subjectId) {
        // Both halves happen server side in one call. Leaving the UI to make two calls would let a revoke
        // half-succeed with no way for the administrator to tell which half had taken effect.
        return revocationServiceProvider.get().revokeAccessForUser(subjectId);
    }
}
