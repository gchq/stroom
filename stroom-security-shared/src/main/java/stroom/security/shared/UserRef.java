/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.shared;

import stroom.query.api.v1.DocRef;
import stroom.security.shared.User.UserStatus;
import stroom.util.shared.SharedObject;

public class UserRef extends DocRef implements SharedObject {
    private static final long serialVersionUID = 5883121212911541301L;

    private boolean group;
    private boolean enabled;

    public UserRef() {
        // Default constructor necessary for GWT serialisation.
    }

    public UserRef(final String type, final String uuid, final String name, final boolean group) {
        super(type, uuid, name);
        this.group = group;
    }

    public UserRef(final String type, final String uuid, final String name, final boolean group, final boolean enabled) {
        super(type, uuid, name);
        this.group = group;
        this.enabled = enabled;
    }

    public static UserRef create(final User user) {
        if (user == null) {
            return null;
        }

        final String type = user.getType();
        final String uuid = user.getUuid();
        final String name = user.getName();

        return new UserRef(type, uuid, name, user.isGroup(), UserStatus.ENABLED.equals(user.getStatus()));
    }

    public boolean isGroup() {
        return group;
    }

    public void setGroup(final boolean group) {
        this.group = group;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }
}