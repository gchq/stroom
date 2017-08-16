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

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.mgt.SecurityManager;
import org.apache.shiro.realm.Realm;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.mgt.DefaultWebSecurityManager;
import org.springframework.stereotype.Component;
import stroom.security.Insecure;

import javax.inject.Inject;

@Component
public class StroomSecurityManager extends DefaultWebSecurityManager implements SecurityManager {
    @Inject
    public StroomSecurityManager(final Realm singleRealm) {
        super(singleRealm);
    }

    @Override
    @Insecure // During login nobody is logged in so all underlying calls need to be performed insecurely.
    public Subject login(final Subject subject, final AuthenticationToken token) throws AuthenticationException {
        return super.login(subject, token);
    }
}
