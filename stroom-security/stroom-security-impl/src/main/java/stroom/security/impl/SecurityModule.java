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

import stroom.event.logging.api.ObjectInfoProviderBinder;
import stroom.security.api.AppPermissionService;
import stroom.security.api.ContentPackUserService;
import stroom.security.api.DocumentPermissionService;
import stroom.security.api.HashFunctionFactory;
import stroom.security.api.ServiceUserFactory;
import stroom.security.api.UserGroupsService;
import stroom.security.api.UserIdentityFactory;
import stroom.security.api.UserService;
import stroom.security.common.impl.ContentSecurityFilter;
import stroom.security.common.impl.DelegatingServiceUserFactory;
import stroom.security.common.impl.ExternalIdpConfigurationProvider;
import stroom.security.common.impl.ExternalServiceUserFactory;
import stroom.security.common.impl.HashFunctionFactoryImpl;
import stroom.security.common.impl.IdpConfigurationProvider;
import stroom.security.common.impl.JwtContextFactory;
import stroom.security.common.impl.RefreshManager;
import stroom.security.common.impl.TestCredentialsServiceUserFactory;
import stroom.security.impl.apikey.ApiKeyObjectInfoProvider;
import stroom.security.impl.apikey.ApiKeyResourceImpl;
import stroom.security.impl.apikey.CreateHashedApiKeyResponseObjectInfoProvider;
import stroom.security.impl.event.PermissionChangeEvent;
import stroom.security.impl.event.PermissionChangeEventLifecycleModule;
import stroom.security.impl.event.PermissionChangeEventModule;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdConfiguration;
import stroom.security.shared.CreateHashedApiKeyResponse;
import stroom.security.shared.HashedApiKey;
import stroom.security.shared.User;
import stroom.security.user.api.UserInfoLookup;
import stroom.security.user.api.UserRefLookup;
import stroom.util.guice.FilterBinder;
import stroom.util.guice.FilterInfo;
import stroom.util.guice.GuiceUtil;
import stroom.util.guice.HasHealthCheckBinder;
import stroom.util.guice.RestResourcesBinder;
import stroom.util.shared.Clearable;

import com.google.inject.AbstractModule;
import io.dropwizard.lifecycle.Managed;

public class SecurityModule extends AbstractModule {

    private static final String MATCH_ALL_PATHS = "/*";

    @Override
    protected void configure() {
        install(new PermissionChangeEventModule());
        install(new PermissionChangeEventLifecycleModule());

        bind(AppPermissionService.class).to(AppPermissionServiceImpl.class);
        bind(DocumentPermissionService.class).to(DocumentPermissionServiceImpl.class);
        bind(UserService.class).to(UserServiceImpl.class);
        bind(ContentPackUserService.class).to(UserServiceImpl.class);
        bind(UserIdentityFactory.class).to(StroomUserIdentityFactory.class);
        bind(JwtContextFactory.class).to(DelegatingJwtContextFactory.class);
        bind(IdpConfigurationProvider.class).to(DelegatingIdpConfigurationProvider.class);
        // Now bind OpenIdConfiguration to the iface from prev bind
        bind(OpenIdConfiguration.class).to(IdpConfigurationProvider.class);
        bind(UserRefLookup.class).to(UserRefLookupImpl.class);
        bind(UserInfoLookup.class).to(UserInfoLookupImpl.class);
        bind(AuthProxyService.class).to(AuthProxyServiceImpl.class);
        bind(UserGroupsService.class).to(UserGroupsCache.class);
        bind(HashFunctionFactory.class).to(HashFunctionFactoryImpl.class);

        HasHealthCheckBinder.create(binder())
                .bind(ExternalIdpConfigurationProvider.class);

        // TODO: 26/07/2023 Remove these
//        bind(ProcessingUserIdentityProvider.class).to(DelegatingProcessingUserIdentityProvider.class);
//        GuiceUtil.buildMapBinder(binder(), IdpType.class, ProcessingUserIdentityProvider.class)
//                .addBinding(IdpType.EXTERNAL_IDP, ExternalProcessingUserIdentityProvider.class);

        bind(ServiceUserFactory.class).to(DelegatingServiceUserFactory.class);
        GuiceUtil.buildMapBinder(binder(), IdpType.class, ServiceUserFactory.class)
                .addBinding(IdpType.EXTERNAL_IDP, ExternalServiceUserFactory.class)
                .addBinding(IdpType.TEST_CREDENTIALS, TestCredentialsServiceUserFactory.class);

        FilterBinder.create(binder())
                .bind(new FilterInfo(ContentSecurityFilter.class.getSimpleName(), MATCH_ALL_PATHS),
                        ContentSecurityFilter.class)
                .bind(new FilterInfo(SecurityFilter.class.getSimpleName(), MATCH_ALL_PATHS),
                        SecurityFilter.class);

        GuiceUtil.buildMultiBinder(binder(), Managed.class)
                .addBinding(RefreshManager.class);

        // Provide object info to the logging service.
        GuiceUtil.buildMultiBinder(binder(), Clearable.class)
                .addBinding(UserDocumentPermissionsCache.class)
                .addBinding(UserAppPermissionsCache.class)
                .addBinding(UserGroupsCache.class)
                .addBinding(UserCache.class)
                .addBinding(StroomUserIdentityFactory.class);

        GuiceUtil.buildMultiBinder(binder(), PermissionChangeEvent.Handler.class)
                .addBinding(UserCache.class)
                .addBinding(UserInfoCache.class)
                .addBinding(UserGroupsCache.class)
                .addBinding(UserAppPermissionsCache.class)
                .addBinding(StroomUserIdentityFactory.class)
                .addBinding(UserDocumentPermissionsCache.class);

        RestResourcesBinder.create(binder())
                .bind(ApiKeyResourceImpl.class)
                .bind(AppPermissionResourceImpl.class)
                .bind(DocPermissionResourceImpl.class)
                .bind(SessionResourceImpl.class)
                .bind(UserResourceImpl.class)
                .bind(UserRefResourceImpl.class)
                .bind(UserInfoResourceImpl.class)
                .bind(AuthProxyResourceImpl.class);

        ObjectInfoProviderBinder.create(binder())
                .bind(User.class, UserObjectInfoProvider.class)
                .bind(HashedApiKey.class, ApiKeyObjectInfoProvider.class)
                .bind(CreateHashedApiKeyResponse.class, CreateHashedApiKeyResponseObjectInfoProvider.class);
    }
}
