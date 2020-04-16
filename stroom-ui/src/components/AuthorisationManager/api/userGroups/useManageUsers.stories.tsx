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

import useManageUsers from "./useManageUsers";
import Button from "components/Button";
import { loremIpsum } from "lorem-ipsum";

const TestHarness: React.FunctionComponent = () => {
  const { users, createUser, deleteUser } = useManageUsers();

  const onClickCreateUser = React.useCallback(() => {
    createUser(loremIpsum({ count: 1, units: "words" }), false);
  }, [createUser]);

  return (
    <div>
      <Button onClick={onClickCreateUser} text="Create User" />
      <h2>Users</h2>
      <ul>
        {users.map(user => (
          <div key={user.uuid}>
            <Button onClick={() => deleteUser(user.uuid)} text="Delete" />
            {JSON.stringify(user)}
          </div>
        ))}
      </ul>
    </div>
  );
};

storiesOf("Sections/Authorisation Manager/useManageUsers", module).add(
  "Sample 1",
  () => <TestHarness />,
);
