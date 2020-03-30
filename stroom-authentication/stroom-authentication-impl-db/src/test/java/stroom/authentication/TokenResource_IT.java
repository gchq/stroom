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

import stroom.authentication.resources.user.v1.User;
import stroom.authentication.resources.token.v1.Token;

public abstract class TokenResource_IT extends Dropwizard_IT {
    protected final String url = tokenManager.getRootUrl();
    protected final String searchUrl = tokenManager.getRootUrl() + "/search";

    protected String clearTokensAndLogin() throws Exception {
        String jwsToken = authenticationManager.loginAsAdmin();
        tokenManager.deleteAllTokens(jwsToken);
        // We've just deleted all the tokens so we'll need to log in again.
        String refreshedToken = authenticationManager.loginAsAdmin();
        return refreshedToken;
    }

    protected void createUserAndTokens(String userEmail, String jwsToken) throws Exception {
        userManager.createUser(new User(userEmail, "password"), jwsToken);
        tokenManager.createToken(userEmail, Token.TokenType.USER, jwsToken);
        tokenManager.createToken(userEmail, Token.TokenType.API, jwsToken);
        tokenManager.createToken(userEmail, Token.TokenType.EMAIL_RESET, jwsToken);
    }

}
