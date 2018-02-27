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

package stroom.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import stroom.util.spring.StroomScope;

@Configuration
public class SecurityContextSpringConfig {
    @Bean
//    @Profile(PROD_SECURITY)
    @Scope(value = StroomScope.PROTOTYPE, proxyMode = ScopedProxyMode.INTERFACES)
    public SecurityContext securityContext(final DocumentPermissionsCache documentPermissionsCache,
                                           final UserGroupsCache userGroupsCache,
                                           final UserAppPermissionsCache userAppPermissionsCache,
                                           final UserCache userCache,
                                           final DocumentPermissionService documentPermissionService,
                                           final DocumentTypePermissions documentTypePermissions,
                                           final AuthenticationServiceClients authenticationServiceClients) {
        return new SecurityContextImpl(documentPermissionsCache, userGroupsCache, userAppPermissionsCache, userCache, documentPermissionService, documentTypePermissions, authenticationServiceClients);
    }
}