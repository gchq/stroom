import { storiesOf } from "@storybook/react";
import * as React from "react";
import UserListDialog from "./UserListDialog";

import fullTestData from "testing/data";

const stories = storiesOf("Users", module);
stories.add("List", () => (
  <UserListDialog
    onNewUserClicked={() => undefined}
    onUserOpen={() => undefined}
    onUserDelete={() => undefined}
    users={fullTestData.users}
    onClose={() => undefined}
  />
));
