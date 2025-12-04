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

package stroom.security.impl;

import stroom.event.logging.rs.api.AutoLogged;
import stroom.event.logging.rs.api.AutoLogged.OperationType;
import stroom.security.api.UserService;
import stroom.security.shared.FindUserCriteria;
import stroom.security.shared.GetUserRequest;
import stroom.security.shared.User;
import stroom.security.shared.UserFields;
import stroom.security.shared.UserRefResource;
import stroom.util.shared.ResultPage;
import stroom.util.shared.UserRef;

import jakarta.inject.Inject;
import jakarta.inject.Provider;

import java.util.List;

@AutoLogged
public class UserRefResourceImpl implements UserRefResource {

    private final Provider<UserService> userServiceProvider;

    @Inject
    public UserRefResourceImpl(final Provider<UserService> userServiceProvider) {
        this.userServiceProvider = userServiceProvider;
    }

    @Override
    public ResultPage<UserRef> find(final FindUserCriteria criteria) {
        // Apply default sort
        if (criteria.getSortList() == null || criteria.getSortList().isEmpty()) {
            criteria.addSort(UserFields.FIELD_IS_GROUP);
            criteria.addSort(UserFields.FIELD_UNIQUE_ID);
        }
        final ResultPage<User> userResultPage = userServiceProvider
                .get()
                .find(criteria);
        final List<UserRef> list = userResultPage
                .getValues()
                .stream()
                .map(User::asRef)
                .toList();
        return new ResultPage<>(list, userResultPage.getPageResponse());
    }

    @AutoLogged(OperationType.UNLOGGED)
    @Override
    public UserRef getUserByUuid(final GetUserRequest request) {
        return userServiceProvider.get().getUserByUuid(request.getUuid(), request.getContext());
    }
}
