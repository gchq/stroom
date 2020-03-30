import * as React from "react";

import { storiesOf } from "@storybook/react";
import JsonDebug from "testing/JsonDebug";
import { useAppPermissionsForUser } from ".";
import fullTestData from "testing/data";

const userUuid = fullTestData.usersAndGroups.users[0].uuid;

const TestHarness: React.FunctionComponent = () => {
  const permissions = useAppPermissionsForUser(userUuid);

  return <JsonDebug value={{ userUuid, permissions }} />;
};

storiesOf(
  "Sections/Authorisation Manager/useAppPermissionsForUser",
  module,
).add("test", () => <TestHarness />);
