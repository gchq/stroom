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

import * as React from "react";

import IconHeader from "components/IconHeader";
import { themeOptions, useTheme, ThemeOption } from "lib/useTheme/useTheme";
import Select from "react-select";

const UserSettings: React.FunctionComponent = () => {
  const { theme, setTheme } = useTheme();

  const value: ThemeOption = React.useMemo(
    () => themeOptions.find((t) => t.value === theme) || themeOptions[0],
    [theme],
  );
  const onChange = React.useCallback(
    (d: ThemeOption) => {
      setTheme(d.value);
    },
    [setTheme],
  );

  return (
    <div className="UserSettings">
      <IconHeader text="User Settings" icon="user" />
      <div className="UserSettings__container">
        <div>
          <label>Theme:</label>
          <Select
            onChange={onChange}
            value={value}
            options={themeOptions}
            getOptionLabel={(d) => d.text}
            getOptionValue={(d) => d.value}
          />
        </div>
      </div>
    </div>
  );
};

export default UserSettings;
