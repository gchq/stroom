import * as React from "react";
import { storiesOf } from "@storybook/react";

import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import { AlertDisplayBoundary, ErrorOutlet, UsingErrorHook, UsingErrorInlet } from "./AlertDisplayBoundary";

const TestHarness: React.FunctionComponent = () => {
  return (
    <AlertDisplayBoundary>
      <p>Here be errors...</p>
      <ErrorOutlet/>
      <hr/>
      <UsingErrorInlet/>
      <hr/>
      <UsingErrorHook/>
    </AlertDisplayBoundary>
  );
};

const stories = storiesOf(
  "AlertDialog",
  module,
);
addThemedStories(stories, "AlertDisplay",() => <TestHarness/>);
