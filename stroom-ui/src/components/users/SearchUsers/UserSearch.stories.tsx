import { action } from "@storybook/addon-actions";
import { storiesOf } from "@storybook/react";
import * as React from "react";
import UserSearch from "./UserSearch";

import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import fullTestData from "testing/data";

const stories = storiesOf("Users/Search", module);

addThemedStories(stories, () => (
  <UserSearch
    onNewUserClicked={() => action("onNewUserClicked")}
    onUserOpen={(userId: string) => action(`onUserOpen:${userId}`)}
    onDeleteUser={(userId: string) => action(`onDeleteUser: ${userId}`)}
    users={fullTestData.users}
  />
));
