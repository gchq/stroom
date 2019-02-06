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
import StroomDecorator from "../../lib/storybook/StroomDecorator";
import fullTestData from "../../lib/storybook/fullTestData";
import { Switch, Route, RouteComponentProps } from "react-router";

import "../../styles/main.css";
import { User } from "src/types";
import UsersInGroup from "../../components/UserPermissionEditor/UsersInGroup";
import GroupsForUser from "../../components/UserPermissionEditor/GroupsForUser";
import UserPermissionEditor from "../../components/UserPermissionEditor";

// Pick a group
let aUser: User = fullTestData.usersAndGroups.users
  .filter(u => !u.isGroup)
  .pop()!;
let aGroup: User = fullTestData.usersAndGroups.users
  .filter(u => u.isGroup)
  .pop()!;

const UserPermissionsWithRouter = () => (
  <Switch>
    <Route
      exact
      path="/s/userPermissions/:userUuid"
      render={(props: RouteComponentProps<any>) => (
        <UserPermissionEditor
          userUuid={props.match.params.userUuid}
          listingId="storybook"
        />
      )}
    />
    <Route component={UserPermissions} />
  </Switch>
);

storiesOf("Sections/User Permissions", module)
  .addDecorator(StroomDecorator)
  .add("User Permissions", () => <UserPermissionsWithRouter />)
  .add("Groups For User", () => <GroupsForUser user={aUser} />)
  .add("Users In Group", () => <UsersInGroup group={aGroup} />);
