import * as React from "react";
import { storiesOf } from "@storybook/react";

import {
  AlertDisplayBoundary,
  AlertOutlet,
  UsingAlertHook,
  UsingAlertInlet,
} from "./AlertDisplayBoundary";

const TestHarness: React.FunctionComponent = () => {
  return (
    <AlertDisplayBoundary>
      <p>Here be errors...</p>
      <AlertOutlet />
      <hr />
      <UsingAlertInlet />
      <hr />
      <UsingAlertHook />
    </AlertDisplayBoundary>
  );
};

storiesOf("AlertDialog", module).add("AlertDisplay", () => <TestHarness />);
