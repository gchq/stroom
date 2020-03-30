import * as React from "react";
import { storiesOf } from "@storybook/react";
import useWelcomeHtml from "./useWelcomeHtml";
import JsonDebug from "testing/JsonDebug";

const TestHarness: React.FunctionComponent = () => {
  const welcomeData = useWelcomeHtml();

  return <JsonDebug value={welcomeData} />;
};

storiesOf("Sections/Welcome/useWelcomeHtml", module).add("test", () => (
  <TestHarness />
));
