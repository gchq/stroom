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

import fullTestData from "testing/data";
import { storiesOf } from "@storybook/react";

import AppPermissionPicker from "./AppPermissionPicker";

import JsonDebug from "testing/JsonDebug";

const TestForm = () => {
  const [value, setAppPermissions] = React.useState<string[]>(
    fullTestData.allAppPermissions.slice(0, 3),
  );

  const addPermission = React.useCallback(
    (permissionName: string) => {
      setAppPermissions(value.concat([permissionName]));
    },
    [setAppPermissions, value],
  );
  const removePermission = React.useCallback(
    (permissionName: string) => {
      setAppPermissions(value.filter((a) => a !== permissionName));
    },
    [setAppPermissions, value],
  );

  return (
    <div>
      <AppPermissionPicker
        {...{
          value,
          addPermission,
          removePermission,
        }}
      />
      <JsonDebug value={value} />
    </div>
  );
};

storiesOf("Sections/Authorisation Manager", module).add(
  "App Permission Picker",
  () => <TestForm />,
);
