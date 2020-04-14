import * as React from "react";

import { storiesOf } from "@storybook/react";

import useApi from "./useApi";
import JsonDebug from "testing/JsonDebug";
import { SessionInfo } from "./types";
const TestHarness: React.FunctionComponent = () => {
  // REST call promise
  const { getSessionInfo } = useApi();
  const [sessionInfo, setSessionInfo] = React.useState<SessionInfo>(undefined);
  React.useEffect(() => {
    getSessionInfo().then(setSessionInfo);
  }, [getSessionInfo, setSessionInfo]);
  return (
    <div>
      <JsonDebug value={{ sessionInfo }} />
    </div>
  );
};

storiesOf("Sections/SessionInfo/useApi", module).add("test", () => (
  <TestHarness />
));
