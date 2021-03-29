import * as React from "react";
import { storiesOf } from "@storybook/react";

import Prompt, { Confirm, PromptProps, PromptType } from "./Prompt";
import { OkCancelProps } from "../Dialog/OkCancelButtons";

const TestPrompt: React.FunctionComponent = () => {
  const promptProps: PromptProps = {
    type: PromptType.ERROR,
    title: "Test",
    message: "Ouch, that hurts!",
  };

  return <Prompt promptProps={promptProps} onCloseDialog={() => undefined} />;
};

const TestConfirm: React.FunctionComponent = () => {
  const promptProps: PromptProps = {
    type: PromptType.ERROR,
    title: "Test",
    message: "Ouch, that hurts!",
  };

  const okCancelProps: OkCancelProps = {};

  return <Confirm promptProps={promptProps} okCancelProps={okCancelProps} />;
};

storiesOf("Prompt", module).add("Prompt", () => <TestPrompt />);
storiesOf("Prompt", module).add("Confirm", () => <TestConfirm />);
