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
 *
 */

package stroom.security;

import event.logging.BaseObject;
import event.logging.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.entity.shared.BaseCriteria;
import stroom.event.logging.api.ObjectInfoProvider;
import stroom.security.shared.FindUserCriteria;

class UserObjectInfoProvider implements ObjectInfoProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserObjectInfoProvider.class);

    @Override
    public BaseObject createBaseObject(final java.lang.Object obj) {
        if (obj instanceof stroom.security.User) {
            final stroom.security.User user = (stroom.security.User) obj;
                final User usr = new User();
                usr.setId(user.getName());

                // Add state.
                try {
                    switch (user.getStatus()) {
                        case ENABLED:
                            usr.setState("Enabled");
                            break;
                        case DISABLED:
                            usr.setState("Disabled");
                            break;
                        case LOCKED:
                            usr.setState("Locked");
                            break;
                    }
                } catch (final RuntimeException ex) {
                    LOGGER.error("Unable to set user state!", ex);
                }

                return usr;
            }

        return null;
    }

    @Override
    public String getObjectType(final java.lang.Object object) {
        if (object == null) {
            return null;
        }
        if (object instanceof BaseCriteria) {
            final BaseCriteria criteria = (BaseCriteria) object;

            if (criteria instanceof FindUserCriteria) {
                return "User";
            }

            String name = criteria.getClass().getSimpleName();
            final StringBuilder sb = new StringBuilder();
            final char[] chars = name.toCharArray();
            for (final char c : chars) {
                if (Character.isUpperCase(c)) {
                    sb.append(" ");
                }
                sb.append(c);
            }
            name = sb.toString().trim();
            final int start = name.indexOf(" ");
            final int end = name.lastIndexOf(" ");
            if (start != -1 && end != -1) {
                name = name.substring(start + 1, end);
            }

            return name;
        }

        return object.getClass().getSimpleName();
    }
}
