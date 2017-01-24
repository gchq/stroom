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

package stroom.security.shared;

import stroom.query.api.DocRef;
import stroom.util.shared.SharedObject;

public class UserRef extends DocRef implements SharedObject {
    private static final long serialVersionUID = 5883121212911541301L;

    private boolean group;

    public UserRef() {
        // Default constructor necessary for GWT serialisation.
    }

    public UserRef(final String type, final String uuid, final String name, final boolean group) {
        super(type, uuid, name);
        this.group = group;
    }

    public static UserRef create(final User user) {
        if (user == null) {
            return null;
        }

        final String type = user.getType();
        final String uuid = user.getUuid();
        final String name = user.getName();

        return new UserRef(type, uuid, name, user.isGroup());
    }

    public boolean isGroup() {
        return group;
    }

    public void setGroup(final boolean group) {
        this.group = group;
    }
}
