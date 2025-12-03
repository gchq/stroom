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

import stroom.credentials.impl.CredentialsModule;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.SecurityContext;
import stroom.security.api.UserGroupsService;
import stroom.security.mock.MockSecurityContext;
import stroom.test.common.util.db.DbTestModule;

import com.google.inject.AbstractModule;

public class TestModule extends AbstractModule {

    @Override
    protected void configure() {
        super.configure();
        install(new CredentialsDaoModule());
        install(new CredentialsDbModule());
        install(new CredentialsModule());
        install(new DbTestModule());

        bind(SecurityContext.class).to(MockSecurityContext.class);
        bind(DocumentPermissionService.class).to(MockDocumentPermissionService.class);
        bind(UserGroupsService.class).to(MockUserGroupsService.class);
    }
}
