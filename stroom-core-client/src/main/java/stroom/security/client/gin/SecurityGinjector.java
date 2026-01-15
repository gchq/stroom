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

package stroom.security.client.gin;

import stroom.security.client.ApiKeysPlugin;
import stroom.security.client.LoginManager;
import stroom.security.client.LogoutPlugin;
import stroom.security.client.api.ClientSecurityContext;

import com.google.gwt.inject.client.AsyncProvider;
import com.google.gwt.inject.client.Ginjector;
import com.google.inject.Provider;

public interface SecurityGinjector extends Ginjector {

    LoginManager getLoginManager();

    AsyncProvider<LogoutPlugin> getLogoutPlugin();

    AsyncProvider<ApiKeysPlugin> getApiKeysPlugin();

    Provider<ClientSecurityContext> getSecurityContext();
}
