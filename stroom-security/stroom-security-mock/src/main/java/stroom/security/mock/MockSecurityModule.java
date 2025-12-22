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

package stroom.security.mock;

import stroom.security.api.AppPermissionService;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.HashFunctionFactory;
import stroom.security.api.UserService;
import stroom.test.common.util.guice.GuiceTestUtil;

import com.google.inject.AbstractModule;

public class MockSecurityModule extends AbstractModule {

    @Override
    protected void configure() {
        GuiceTestUtil.buildMockBinder(binder())
                .addMockBindingFor(UserService.class)
                .addMockBindingFor(DocumentPermissionService.class)
                .addMockBindingFor(AppPermissionService.class);

        bind(HashFunctionFactory.class).to(MockHashFunctionFactory.class);
    }
}
