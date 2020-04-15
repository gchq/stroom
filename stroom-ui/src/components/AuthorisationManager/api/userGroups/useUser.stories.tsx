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
import useUser from "./useUser";
import Button from "components/Button";

const users = fullTestData.usersAndGroups.users;

const TestHarness = () => {
  const [testListIndex, setTestListIndex] = React.useState<number>(0);

  const userUuid = React.useMemo(() => users[testListIndex].uuid, [
    testListIndex,
  ]);
  const user = useUser(userUuid);

  const switchList = React.useCallback(() => {
    setTestListIndex((testListIndex + 1) % users.length);
  }, [setTestListIndex, testListIndex]);

  return (
    <div>
      <Button onClick={switchList} text="Switch List" />
      <h2>User UUIDS</h2>
      <p>{userUuid}</p>
      <h2>Users</h2>
      <ul>{JSON.stringify(user)}</ul>
    </div>
  );
};

storiesOf("Sections/Authorisation Manager/useUser", module).add(
  "Sample 1",
  () => <TestHarness />,
);
