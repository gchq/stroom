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

package stroom.security.server;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.credential.CredentialsMatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.inject.Inject;

//TODO We don't really need this any more but it's required by Shiro. Can we remove it somehow?
@Component
public class MyCredentialsMatcher implements CredentialsMatcher {
    private static final Logger LOGGER = LoggerFactory.getLogger(MyCredentialsMatcher.class);

    private final PasswordEncoder passwordEncoder;

    @Inject
    public MyCredentialsMatcher(final PasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public boolean doCredentialsMatch(final AuthenticationToken token, final AuthenticationInfo info) {
        if (token == null) {
            return false;
        }

        if (token instanceof JWTAuthenticationToken) {
            return info != null;
        }

        return false;
    }
}
