import * as React from "react";
import { storiesOf } from "@storybook/react";
import UsersTable, { useTable } from "./UsersTable";
import fullTestData from "testing/data";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";

const stories = storiesOf("Sections/Authorisation Manager", module);

const TestHarness: React.FunctionComponent = () => {
  const { componentProps } = useTable(fullTestData.usersAndGroups.users);

  return <UsersTable {...componentProps} />;
};

addThemedStories(stories, "Users Table", () => <TestHarness />);
