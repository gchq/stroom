import * as React from "react";
import { storiesOf } from "@storybook/react";

import Prompt, { PromptProps, PromptType } from "./Prompt";

const TestHarness: React.FunctionComponent = () => {
  const promptProps: PromptProps = {
    type: PromptType.ERROR,
    title: "Test",
    message: "Ouch, that hurts!",
  };

  return <Prompt promptProps={promptProps} onCloseDialog={() => undefined} />;
};

storiesOf("Prompt", module).add("Dialog", () => <TestHarness />);
