/*
 * Copyright 2018 Crown Copyright
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

package stroom.auth;

import com.google.inject.AbstractModule;

import stroom.auth.resources.authentication.v1.AuthenticationResource;
import stroom.auth.resources.token.v1.TokenResource;
import stroom.auth.resources.user.v1.UserResource;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.RestResource;

public class AuthModule extends AbstractModule {
    @Override
    protected void configure() {

        GuiceUtil.buildMultiBinder(binder(), RestResource.class)
                .addBinding(AuthenticationResource.class)
                .addBinding(TokenResource.class)
                .addBinding(UserResource.class);

    }
}