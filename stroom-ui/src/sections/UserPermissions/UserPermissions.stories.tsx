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

import UserPermissions from ".";
import StroomDecorator, { testData } from "../../lib/storybook/StroomDecorator";

import "../../styles/main.css";
import { User } from "src/types";
import UsersInGroup from "./UsersInGroup";
import GroupsForUser from "./GroupsForUser";

// Pick a group
let aUser: User = testData.usersAndGroups.users.filter(u => !u.group).pop()!;
let aGroup: User = testData.usersAndGroups.users.filter(u => u.group).pop()!;

storiesOf("Sections/User Permissions", module)
  .addDecorator(StroomDecorator)
  .add("User Permissions", () => <UserPermissions />)
  .add("Groups For User", () => <GroupsForUser user={aUser} />)
  .add("Users In Group", () => <UsersInGroup group={aGroup} />);
