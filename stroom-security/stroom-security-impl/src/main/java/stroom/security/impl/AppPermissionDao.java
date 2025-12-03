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

import stroom.security.shared.AppPermission;
import stroom.security.shared.AppUserPermissions;
import stroom.security.shared.FetchAppUserPermissionsRequest;
import stroom.util.shared.ResultPage;

import java.util.Set;

public interface AppPermissionDao {

    Set<AppPermission> getPermissionsForUser(String userUuid);

    void addPermission(String userUuid, AppPermission permission);

    void removePermission(String userUuid, AppPermission permission);

    ResultPage<AppUserPermissions> fetchAppUserPermissions(FetchAppUserPermissionsRequest request);
}
