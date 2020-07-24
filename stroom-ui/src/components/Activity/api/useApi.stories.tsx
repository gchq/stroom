import * as React from "react";

import { storiesOf } from "@storybook/react";

import { useApi } from "./useApi";
import JsonDebug from "testing/JsonDebug";
import { Activity } from "./types";
const CurrentActivityTestHarness: React.FunctionComponent = () => {
  // REST call promise
  const { getCurrentActivity } = useApi();

  const [currentActivity, setCurrentActivity] = React.useState<Activity>(
    undefined,
  );
  React.useEffect(() => {
    getCurrentActivity().then(setCurrentActivity);
  }, [getCurrentActivity, setCurrentActivity]);
  return (
    <div>
      <JsonDebug value={{ currentActivity }} />
    </div>
  );
};

const ActivitiesTestHarness: React.FunctionComponent = () => {
  // REST call promise
  const { getActivities } = useApi();

  const [activities, setActivities] = React.useState<Activity[]>(undefined);
  React.useEffect(() => {
    getActivities().then(setActivities);
  }, [getActivities, setActivities]);
  return (
    <div>
      <JsonDebug value={{ activities }} />
    </div>
  );
};

storiesOf("Sections/Activity/useAuthenticationResource", module).add(
  "test",
  () => (
    <div>
      <CurrentActivityTestHarness />
      <ActivitiesTestHarness />
    </div>
  ),
);
