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

package stroom.authentication;

import com.google.inject.AbstractModule;
import stroom.authentication.account.AccountModule;
import stroom.authentication.authenticate.AuthenticateModule;
import stroom.authentication.oauth2.OAuth2Module;
import stroom.authentication.token.TokenModule;

public final class AuthModule extends AbstractModule {
    @Override
    protected void configure() {
        install(new AccountModule());
        install(new AuthenticateModule());
        install(new OAuth2Module());
        install(new TokenModule());
    }
}
