/*
 * Copyright 2016 Crown Copyright
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

package stroom.security.client.gin;

import stroom.core.client.gin.PluginModule;
import stroom.security.client.ApiKeysPlugin;
import stroom.security.client.AppPermissionsPlugin;
import stroom.security.client.CurrentUser;
import stroom.security.client.DocumentPermissionsPlugin;
import stroom.security.client.LoginManager;
import stroom.security.client.LogoutPlugin;
import stroom.security.client.SignOutOtherSessionsPlugin;
import stroom.security.client.SigningKeyPlugin;
import stroom.security.client.UserAccessPlugin;
import stroom.security.client.UserPlugin;
import stroom.security.client.UserTabPlugin;
import stroom.security.client.UsersAndGroupsPlugin;
import stroom.security.client.UsersPlugin;
import stroom.security.client.api.ClientSecurityContext;

import com.google.inject.Singleton;

public class SecurityModule extends PluginModule {

    @Override
    protected void configure() {
        bind(ClientSecurityContext.class).to(CurrentUser.class).in(Singleton.class);

        bind(LoginManager.class).in(Singleton.class);

        bindPlugin(LogoutPlugin.class);
        bindPlugin(SignOutOtherSessionsPlugin.class);

        // Users
        bindPlugin(AppPermissionsPlugin.class);
        bindPlugin(DocumentPermissionsPlugin.class);
        bindPlugin(UserPlugin.class);
        bindPlugin(UsersPlugin.class);
        bindPlugin(UserTabPlugin.class);
        bindPlugin(UsersAndGroupsPlugin.class);
        bindPlugin(ApiKeysPlugin.class);
        bindPlugin(UserAccessPlugin.class);
        bindPlugin(SigningKeyPlugin.class);
    }
}
