import * as React from "react";
import { storiesOf } from "@storybook/react";
import useBuildInfo from "./useSessionInfo";
import JsonDebug from "testing/JsonDebug";

const TestHarness: React.FunctionComponent = () => {
  const buildInfo = useBuildInfo();

  return <JsonDebug value={buildInfo} />;
};

storiesOf("Sections/SessionInfo/useBuildInfo", module).add("test", () => (
  <TestHarness />
));
