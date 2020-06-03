import * as React from "react";
import { storiesOf } from "@storybook/react";

import AlertDialog, { Alert, AlertType } from "./AlertDialog";

import { addThemedStories } from "testing/storybook/themedStoryGenerator";

const TestHarness: React.FunctionComponent = () => {
  const alert: Alert = {
    type: AlertType.ERROR,
    title: "Test",
    message: "Ouch, that hurts!",
  };

  return (
    <AlertDialog alert={alert} isOpen={true} onCloseDialog={undefined}/>
  );
};

const stories = storiesOf(
  "AlertDialog",
  module,
);
addThemedStories(stories, "Dialog", () => <TestHarness/>);
