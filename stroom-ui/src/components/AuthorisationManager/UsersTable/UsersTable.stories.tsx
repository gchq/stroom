import * as React from "react";
import { storiesOf } from "@storybook/react";
import UsersTable, { useTable } from "./UsersTable";
import fullTestData from "testing/data";

const TestHarness: React.FunctionComponent = () => {
  const { componentProps } = useTable(fullTestData.usersAndGroups.users);

  return <UsersTable {...componentProps} />;
};

storiesOf("Sections/Authorisation Manager", module).add("Users Table", () => (
  <TestHarness />
));
