import * as React from "react";

import { storiesOf } from "@storybook/react";
import JsonDebug from "testing/JsonDebug";
import useAllAppPermissions from "./useAllAppPermissions";

const TestHarness: React.FunctionComponent = () => {
  const all = useAllAppPermissions();

  return <JsonDebug value={all} />;
};

storiesOf("Sections/Authorisation Manager/useAllAppPermissions", module).add(
  "test",
  () => <TestHarness />,
);
