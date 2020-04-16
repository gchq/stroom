import * as React from "react";

import { storiesOf } from "@storybook/react";
import useConfig from "./useConfig";
import JsonDebug from "testing/JsonDebug";

const TestHarness: React.FunctionComponent = () => {
  const config = useConfig();

  return <JsonDebug value={config} />;
};

storiesOf("startup/useConfig", module).add("test", () => <TestHarness />);
