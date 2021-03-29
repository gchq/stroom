import * as React from "react";

import { storiesOf } from "@storybook/react";

import ModeOptionButtons, { useModeOptionButtons } from "./ModeOptionButtons";
import JsonDebug from "testing/JsonDebug";

const TestHarness = () => {
  const { searchMode, componentProps } = useModeOptionButtons();

  return (
    <div>
      <ModeOptionButtons {...componentProps} />
      <JsonDebug value={{ searchMode }} />
    </div>
  );
};
storiesOf("App Search Bar", module).add("Mode Option", () => <TestHarness />);
