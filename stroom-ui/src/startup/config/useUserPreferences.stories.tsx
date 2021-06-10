import * as React from "react";

import { storiesOf } from "@storybook/react";
import useConfig from "./useConfig";
import JsonDebug from "testing/JsonDebug";
import useUserPreferences from "./useUserPreferences";

const TestHarness: React.FunctionComponent = () => {
  const config = useUserPreferences();

  return <JsonDebug value={config} />;
};

storiesOf("startup/useUserPreferences", module).add("test", () => (
  <TestHarness />
));
