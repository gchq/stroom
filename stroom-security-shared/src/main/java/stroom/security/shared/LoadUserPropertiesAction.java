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

import stroom.entity.shared.Action;

public class LoadUserPropertiesAction extends Action<UserProperties> {
    private static final long serialVersionUID = -6740095230475597845L;

    private UserRef userRef;

    public LoadUserPropertiesAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public LoadUserPropertiesAction(final UserRef userRef) {
        this.userRef = userRef;
    }

    public UserRef getUserRef() {
        return userRef;
    }

    @Override
    public String getTaskName() {
        return "Load User Properties";
    }
}
