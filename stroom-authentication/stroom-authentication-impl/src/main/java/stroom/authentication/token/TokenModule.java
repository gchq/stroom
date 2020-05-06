/*
 * Copyright 2017 Crown Copyright
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

package stroom.authentication.token;

import stroom.authentication.api.JsonWebKeyFactory;
import stroom.util.guice.HasHealthCheckBinder;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;

public final class TokenModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(TokenService.class).to(TokenServiceImpl.class);
        bind(TokenEventLog.class).to(TokenEventLogImpl.class);
        bind(JsonWebKeyFactory.class).to(JsonWebKeyFactoryImpl.class);

        RestResourcesBinder.create(binder())
                .bind(TokenResourceImpl.class);

        HasHealthCheckBinder.create(binder())
                .bind(TokenServiceImpl.class);
    }
}
