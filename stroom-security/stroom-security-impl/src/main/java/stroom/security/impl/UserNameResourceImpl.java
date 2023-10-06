/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.security.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.shared.FindUserNameCriteria;
import stroom.security.shared.UserNameResource;
import stroom.security.user.api.UserNameService;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserName;

import javax.inject.Inject;
import javax.inject.Provider;

@AutoLogged(OperationType.MANUALLY_LOGGED)
class UserNameResourceImpl implements UserNameResource {

    private final Provider<UserNameService> userNameServiceProvider;

    @Inject
    public UserNameResourceImpl(final Provider<UserNameService> userNameServiceProvider) {
        this.userNameServiceProvider = userNameServiceProvider;
    }

    @Override
    public ResultPage<UserName> find(final FindUserNameCriteria criteria) {
        return userNameServiceProvider.get().find(criteria);
    }

    @Override
    public ResultPage<UserName> findAssociates(final FindUserNameCriteria criteria) {
        return userNameServiceProvider.get().findAssociates(criteria);
    }

    @Override
    public UserName getByDisplayName(final String displayName) {
        return userNameServiceProvider.get()
                .getByDisplayName(displayName)
                .orElse(null);
    }

    @Override
    public UserName getBySubjectId(final String subjectId) {
        return userNameServiceProvider.get()
                .getBySubjectId(subjectId)
                .orElse(null);
    }

    @Override
    public UserName getByUuid(final String userUuid) {
        return userNameServiceProvider.get()
                .getByUuid(userUuid)
                .orElse(null);
    }
}

