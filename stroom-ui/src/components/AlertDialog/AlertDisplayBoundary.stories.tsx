import * as React from "react";
import { storiesOf } from "@storybook/react";

import {
  AlertDisplayBoundary,
  ErrorOutlet,
  UsingErrorHook,
  UsingErrorInlet,
} from "./AlertDisplayBoundary";

const TestHarness: React.FunctionComponent = () => {
  return (
    <AlertDisplayBoundary>
      <p>Here be errors...</p>
      <ErrorOutlet />
      <hr />
      <UsingErrorInlet />
      <hr />
      <UsingErrorHook />
    </AlertDisplayBoundary>
  );
};

storiesOf("AlertDialog", module).add("AlertDisplay", () => <TestHarness />);
