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
import JsonDebug from "testing/JsonDebug";
import * as Fuse from "fuse.js";

import { useCallback } from "react";
import { User } from "components/users/types";

import {
  disabledUser,
  inactiveUser,
  lockedUser,
  newUser,
  wellUsedUser,
} from "testing/data/users";

import UserSelect from "./UserSelect";
import styled from "styled-components";

const stories = storiesOf("Users/UserSelect", module);

const Container = styled.div`
  width: 30em;
`;

const TestHarness: React.FunctionComponent = () => {
  var initialUsers = [
    disabledUser,
    inactiveUser,
    lockedUser,
    newUser,
    wellUsedUser,
  ];

  const [users, setUsers] = React.useState<User[]>(initialUsers);
  const [selectedUser, setSelectedUser] = React.useState<User>();
  const handleFuzzySearch = useCallback(
    (criteria: string) => {
      var fuse = new Fuse(initialUsers, {
        keys: [{ name: "email", weight: 1 }],
      });
      const searchResults: User[] = fuse.search(criteria);
      setUsers(searchResults);
    },
    [users, setUsers],
  );
  const handleSearch = useCallback(
    (criteria: string) => {
      const searchResults = initialUsers.filter(user =>
        user.email.includes(criteria),
      );
      setUsers(searchResults);
    },
    [users, setUsers],
  );

  const handleChange = useCallback(
    (user: string) => {
      const result = users.find(u => user === u.email);
      setSelectedUser(result);
    },
    [users, selectedUser, setSelectedUser],
  );
  return (
    <Container>
      <h3>Fuzzy search using Fuse.js</h3>
      <UserSelect
        onChange={handleChange}
        onSearch={handleFuzzySearch}
        options={users}
      />

      <h3>Exact match using String.prototype.includes()</h3>
      <UserSelect
        onChange={handleChange}
        onSearch={handleSearch}
        options={users}
      />
      <JsonDebug value={{ selectedUser }} />
      <JsonDebug value={{ users }} />
    </Container>
  );
};

addThemedStories(stories, () => <TestHarness />);
