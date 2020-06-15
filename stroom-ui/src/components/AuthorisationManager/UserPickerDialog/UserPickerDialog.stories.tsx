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

import { storiesOf } from "@storybook/react";

import UserPickerDialog, { useDialog } from "./UserPickerDialog";
import Button from "components/Button";
import JsonDebug from "testing/JsonDebug";

interface Props {
  isGroup: boolean;
}

const TestHarness: React.FunctionComponent<Props> = ({ isGroup }) => {
  const [pickedUser, setPickedUser] = React.useState<string | undefined>(
    undefined,
  );

  const { componentProps, showDialog } = useDialog({
    pickerBaseProps: {
      isGroup,
    },
    onConfirm: setPickedUser,
  });

  return (
    <div>
      <Button text="Show Dialog" onClick={showDialog} />
      <JsonDebug value={{ pickedUser }} />
      <UserPickerDialog {...componentProps} />
    </div>
  );
};

[true, false].forEach((isGroup) => {
  storiesOf("Sections/Authorisation Manager/User Picker Dialog", module).add(
    `${isGroup ? "Group" : "User"}`,
    () => <TestHarness {...{ isGroup }} />,
  );
});
