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

import { addThemedStories } from "testing/storybook/themedStoryGenerator";

import NewUserDialog, { useDialog } from "./NewUserDialog";
import Button from "components/Button";

import JsonDebug from "testing/JsonDebug";

const stories = storiesOf(
  "Sections/Authorisation Manager/New User",
  module,
);

interface Props {
  isGroup: boolean;
}

const TestHarness: React.FunctionComponent<Props> = ({ isGroup }) => {
  const [newUser, setNewUser] = React.useState<object>({});
  const { componentProps, showDialog } = useDialog({
    isGroup,
    onCreateUser: (name, isGroup) => setNewUser({ name, isGroup }),
  });

  return (
    <div>
      <h2>New User Test</h2>
      <Button text="Edit" onClick={showDialog} />
      <fieldset>
        <label>Last Value</label>
        <JsonDebug value={newUser} />
      </fieldset>
      <NewUserDialog {...componentProps} />
    </div>
  );
};

addThemedStories(stories, "Dialog", () => <TestHarness isGroup={false} />);
