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

package stroom.security.identity.client.gin;

import stroom.security.identity.client.component.AuthenticationErrorController;
import stroom.security.identity.client.component.LoginController;
import stroom.security.identity.client.component.ResetPasswordController;

import com.google.inject.Provider;

public interface IdentityGinjector {

    Provider<LoginController> getLoginController();

    Provider<AuthenticationErrorController> getAuthenticationErrorController();

    Provider<ResetPasswordController> getResetPasswordController();
}
