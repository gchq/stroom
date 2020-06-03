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

import { storiesOf } from "@storybook/react";
import * as React from "react";
import Button from "components/Button";
import fullTestData from "testing/data";
import JsonDebug from "testing/JsonDebug";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import { StroomUser } from "../api/userGroups";
import UserPicker, { usePicker } from "./UserPicker";

interface Props {
  isGroup: boolean;
}

const TestHarness: React.FunctionComponent<Props> = ({ isGroup }) => {
  const { userNamesToFilterOut, valuesToFilterOut } = React.useMemo(() => {
    let usersToFilterOut = fullTestData.usersAndGroups.users
      .filter((u: StroomUser) => u.group === isGroup)
      .slice(0, 3);
    let valuesToFilterOut = usersToFilterOut.map(u => u.uuid);
    let userNamesToFilterOut = usersToFilterOut.map(u => u.name);
    return {
      userNamesToFilterOut,
      valuesToFilterOut,
    };
  }, [isGroup]);

  const { pickerProps, reset } = usePicker({
    isGroup,
    valuesToFilterOut,
  });
  const { value } = pickerProps;

  return (
    <div>
      <Button text="reset" onClick={reset} />
      <UserPicker {...pickerProps} />
      <JsonDebug value={{ value, userNamesToFilterOut }} />
    </div>
  );
};

[true, false].forEach(isGroup => {
  const stories = storiesOf(
    "Sections/Authorisation Manager/User Picker",
    module,
  );
  addThemedStories(stories, `${isGroup ? "Group" : "User"}`, () => <TestHarness {...{ isGroup }} />);
});
