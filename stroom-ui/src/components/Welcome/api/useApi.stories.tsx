import * as React from "react";

import { storiesOf } from "@storybook/react";

import useApi from "./useApi";
import JsonDebug from "testing/JsonDebug";
import { WelcomeData } from "./types";
const TestHarness: React.FunctionComponent = () => {
  // REST call promise
  const { getWelcomeHtml } = useApi();
  const [welcomeData, setWelcomeData] = React.useState<WelcomeData>(undefined);
  React.useEffect(() => {
    getWelcomeHtml().then(setWelcomeData);
  }, [getWelcomeHtml, setWelcomeData]);
  return (
    <div>
      <JsonDebug value={{ welcomeData }} />
    </div>
  );
};

storiesOf("Sections/Welcome/useAuthenticationApi", module).add("test", () => <TestHarness />);
