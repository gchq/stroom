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

package stroom.security.server;

import javax.inject.Inject;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.CredentialsMatcher;
import org.springframework.stereotype.Component;

@Component
public class MyCredentialsMatcher implements CredentialsMatcher {
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

        if (token instanceof UsernamePasswordToken) {
            final UsernamePasswordToken usernamePasswordToken = (UsernamePasswordToken) token;

            String password = null;
            if (usernamePasswordToken.getCredentials() != null) {
                password = new String((char[]) usernamePasswordToken.getCredentials());
            }

            final String hashed = (String) info.getCredentials();
            return passwordEncoder.matches(password, hashed);
        }

        if (token instanceof CertificateAuthenticationToken) {
            return info != null;
        }

        return false;
    }
}
