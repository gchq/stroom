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

package stroom.security.impl;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import stroom.security.DocumentPermissionCache;
import stroom.security.Security;
import stroom.security.SecurityContext;
import stroom.util.guice.GuiceUtil;
import stroom.util.shared.Clearable;

import javax.inject.Singleton;

public class SecurityContextModule extends AbstractModule {
    @Override
    protected void configure() {
        bind(SecurityContext.class).to(SecurityContextImpl.class);
        bind(DocumentPermissionCache.class).to(DocumentPermissionCacheImpl.class);

        GuiceUtil.buildMultiBinder(binder(), Clearable.class).addBinding(DocumentPermissionCacheImpl.class);
    }

    @Provides
    @Singleton
    public Security security(final SecurityContext securityContext) {
        return new SecurityImpl(securityContext);
    }
}