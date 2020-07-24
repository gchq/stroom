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

import fullTestData from "testing/data";
import useUsers from "./useUsers";
import Button from "components/Button";

const testUserLists = [
  fullTestData.usersAndGroups.users.slice(0, 3).map((u) => u.uuid),
  fullTestData.usersAndGroups.users.slice(4, 8).map((u) => u.uuid),
  fullTestData.usersAndGroups.users.slice(10, 14).map((u) => u.uuid),
];

const TestHarness = () => {
  const [testListIndex, setTestListIndex] = React.useState<number>(0);

  const userUuids = React.useMemo(() => testUserLists[testListIndex], [
    testListIndex,
  ]);
  const users = useUsers(userUuids);

  const switchList = React.useCallback(() => {
    setTestListIndex((testListIndex + 1) % testUserLists.length);
  }, [setTestListIndex, testListIndex]);

  return (
    <div>
      <Button onClick={switchList}>Switch List</Button>
      <h2>User UUIDS</h2>
      <ul>
        {userUuids.map((userUuid) => (
          <li key={userUuid}>{userUuid}</li>
        ))}
      </ul>
      <h2>Users</h2>
      <ul>
        {users.map((user) => (
          <li key={user.uuid}>{JSON.stringify(user)}</li>
        ))}
      </ul>
    </div>
  );
};

storiesOf("Sections/Authorisation Manager/useUsers", module).add(
  "Sample 1",
  () => <TestHarness />,
);
