import * as React from "react";
import { storiesOf } from "@storybook/react";
import useBuildInfo from "./useBuildInfo";
import JsonDebug from "testing/JsonDebug";

const TestHarness: React.FunctionComponent = () => {
  const buildInfo = useBuildInfo();

  return <JsonDebug value={buildInfo} />;
};

storiesOf("Sections/BuildInfo/useBuildInfo", module).add("test", () => (
  <TestHarness />
));
