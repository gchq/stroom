/*
 * Copyright 2018 Crown Copyright
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
import { useEffect, useState } from "react";
import { useStroomApi } from "lib/useStroomApi";
import { UserPreferences } from "api/stroom";

export const defaultValue: UserPreferences = {
  theme: "Dark",
  editorTheme: "CHROME",
  font: "Roboto",
  fontSize: "Medium",
  dateTimePattern: "YYYY-MM-DDTHH:mm:ss.SSS",
  timeZone: {
    use: "UTC",
  },
};

export interface Api {
  userPreferences: UserPreferences;
}

const useUserPreferencesResource = (): Api => {
  const [userPreferences, setUserPreferences] =
    useState<UserPreferences>(defaultValue);
  const { exec } = useStroomApi();

  useEffect(() => {
    console.log("Fetching user preferences");
    try {
      exec(
        (api) => api.preferences.fetchUserPreferences(),
        (response: UserPreferences) => {
          console.log(
            "Setting user preferences to " + JSON.stringify(response),
          );
          setUserPreferences(response);
        },
      );
    } catch (e) {
      console.log(e);
    }
  }, [exec]);

  return { userPreferences };
};

export { useUserPreferencesResource };

export default useUserPreferencesResource;
