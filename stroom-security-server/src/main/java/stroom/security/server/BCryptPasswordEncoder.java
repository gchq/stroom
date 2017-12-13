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

import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Component;

//TODO Why is this a component?
@Component
public class BCryptPasswordEncoder implements PasswordEncoder {
    @Override
    public String encode(final String password) {
        return BCrypt.hashpw(password, BCrypt.gensalt());
    }

    @Override
    public boolean matches(final String password, final String hashed) {
        if (password == null || hashed == null) {
            return false;
        }

        return BCrypt.checkpw(password, hashed);
    }
}
