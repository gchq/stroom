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

package stroom.credentials.impl.db;

import stroom.security.api.UserGroupsService;
import stroom.util.shared.UserRef;

import java.util.Set;

/**
 * Mock for the tests.
 */
public class MockUserGroupsService implements UserGroupsService {

    /** Always returns an empty set */
    @Override
    public Set<UserRef> getGroups(final UserRef userRef) {
        return Set.of();
    }
}
