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

import stroom.dispatch.shared.Action;
import stroom.util.shared.VoidResult;

public class ChangeUserAction extends Action<VoidResult> {
    private static final long serialVersionUID = 800905016214418723L;

    private UserRef userRef;
    private ChangeSet<UserRef> changedLinkedUsers = new ChangeSet<>();
    private ChangeSet<String> changedAppPermissions = new ChangeSet<>();

    public ChangeUserAction() {
        // Default constructor necessary for GWT serialisation.
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public void setUserRef(UserRef userRef) {
        this.userRef = userRef;
    }

    public ChangeSet<UserRef> getChangedLinkedUsers() {
        return changedLinkedUsers;
    }

    public ChangeSet<String> getChangedAppPermissions() {
        return changedAppPermissions;
    }

    @Override
    public String getTaskName() {
        return "Change User";
    }
}
