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

import stroom.util.shared.SharedObject;

public class UserProperties implements SharedObject {
    private static final long serialVersionUID = 2536752388307664050L;

    private Boolean status;

    private Boolean loginExpires;

    public UserProperties() {
        // Default constructor necessary for GWT serialisation.
    }

    public UserProperties(final Boolean status, final Boolean loginExpires) {
        this.status = status;
        this.loginExpires = loginExpires;
    }

    public Boolean getStatus() {
        return status;
    }

    public Boolean getLoginExpires() {
        return loginExpires;
    }
}
