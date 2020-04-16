import * as React from "react";
import { storiesOf } from "@storybook/react";
import useActivitySummary from "./useActivitySummary";
import JsonDebug from "testing/JsonDebug";

const TestHarness: React.FunctionComponent = () => {
  const activity = useActivitySummary();

  return <JsonDebug value={activity} />;
};

storiesOf("Sections/Activity/useActivitySummary", module).add("test", () => (
  <TestHarness />
));
