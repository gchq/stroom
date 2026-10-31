/*
 * Copyright 2024 Crown Copyright
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

package stroom.ui.config.shared;

import stroom.util.shared.UserRef;

public interface UserPreferencesService {

    UserPreferences fetchDefault();

    /**
     * Delete the user preferences for a user.
     */
    boolean delete(final UserRef userRef);

    /// Removes the Ask Stroom AI settings (model, prompts, dock position and so on) from every user's
    /// preferences, and from the default preferences, so that everyone uses the configured AI defaults again.
    /// Needs the Manage Properties permission.
    ///
    /// @return How many preference records were changed.
    int clearAskStroomAiConfigs();

    /// Removes the choice of Ask Stroom AI model from every user's preferences, and from the default preferences,
    /// so that everyone uses the configured default model; their other AI settings are kept. Needs the Manage
    /// Properties permission.
    ///
    /// @return How many preference records were changed.
    int clearAskStroomAiModels();
}
