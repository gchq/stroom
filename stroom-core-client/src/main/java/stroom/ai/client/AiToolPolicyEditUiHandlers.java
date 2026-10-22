/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.client;

import stroom.util.shared.UserRef;

import com.gwtplatform.mvp.client.UiHandlers;

/// What the tool policy dialog tells its presenter.
public interface AiToolPolicyEditUiHandlers extends UiHandlers {

    /// Called when the user asks to add a user or group to the policy.
    void onAddMember();

    /// Called when the user asks to remove a user or group from the policy.
    ///
    /// @param member The user or group to remove.
    void onRemoveMember(UserRef member);

    /// Called when 'All users' is ticked or unticked.
    void onAllUsersChange();

    /// Called when a category is ticked or unticked, or the access is changed.
    void onGrantChange();
}
