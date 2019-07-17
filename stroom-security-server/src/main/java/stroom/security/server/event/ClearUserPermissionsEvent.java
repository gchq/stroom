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

package stroom.security.server.event;

import java.io.Serializable;

public class ClearUserPermissionsEvent implements PermissionChangeEvent, Serializable {
    private static final long serialVersionUID = -6646086368064417052L;

    private String userUuid;

    public ClearUserPermissionsEvent() {
    }

    public ClearUserPermissionsEvent(final String userUuid) {
        this.userUuid = userUuid;
    }

    public static void fire(final PermissionChangeEventBus eventBus, final String userUuid) {
        eventBus.fire(new ClearUserPermissionsEvent(userUuid));
    }

    public String getUserUuid() {
        return userUuid;
    }
}
