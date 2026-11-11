/*
 * Copyright 2021 Crown Copyright
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

package stroom.config.global.impl;

import stroom.ui.config.shared.UserPreferences;
import stroom.util.shared.UserRef;

import java.util.Optional;

public interface UserPreferencesDao {

    Optional<UserPreferences> fetchDefault();

    Optional<UserPreferences> fetch(UserRef userRef);

    int update(UserRef userRef,
               UserPreferences userPreferences);

    int updateDefault(UserRef userRef,
                      UserPreferences userPreferences);

    int delete(UserRef userRef);

    /// Removes the Ask Stroom AI settings from every user's preferences, and from the default preferences, so
    /// that everyone uses the configured AI defaults again.
    ///
    /// @param userRef The user doing it, recorded as the last to update each changed record.
    /// @return How many preference records were changed.
    int clearAskStroomAiConfigs(UserRef userRef);

    /// Removes the choice of Ask Stroom AI model from every user's preferences, and from the default preferences,
    /// keeping their other AI settings, so that everyone uses the configured default model.
    ///
    /// @param userRef The user doing it, recorded as the last to update each changed record.
    /// @return How many preference records were changed.
    int clearAskStroomAiModels(UserRef userRef);
}
