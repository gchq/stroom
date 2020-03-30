import * as React from "react";

import { storiesOf } from "@storybook/react";

import useApi from "./useApi";
import JsonDebug from "testing/JsonDebug";
import { BuildInfo } from "./types";
const TestHarness: React.FunctionComponent = () => {
  // REST call promise
  const { getBuildInfo } = useApi();
  const [buildInfo, setBuildInfo] = React.useState<BuildInfo>(undefined);
  React.useEffect(() => {
    getBuildInfo().then(setBuildInfo);
  }, [getBuildInfo, setBuildInfo]);
  return (
    <div>
      <JsonDebug value={{ buildInfo }} />
    </div>
  );
};

storiesOf("Sections/BuildInfo/useApi", module).add("test", () => (
  <TestHarness />
));
