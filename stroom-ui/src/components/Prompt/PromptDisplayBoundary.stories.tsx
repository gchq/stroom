import * as React from "react";
import { FunctionComponent } from "react";
import { storiesOf } from "@storybook/react";

import { PromptDisplayBoundary, usePrompt } from "./PromptDisplayBoundary";
import { ContentProps } from "./Prompt";

export const UsingPromptHook: FunctionComponent = () => {
  const {
    showPrompt,
    showInfo,
    showWarning,
    showError,
    showFatal,
  } = usePrompt();

  const content: ContentProps = {
    title: "Test",
    message: "Ouch, that hurts!",
  };

  return (
    <>
      <h2>Via hook</h2>
      <button onClick={() => showInfo(content)}>Info</button>
      <button onClick={() => showWarning(content)}>Warning</button>
      <button onClick={() => showError(content)}>Error</button>
      <button onClick={() => showFatal(content)}>Fatal</button>
      <button onClick={() => showPrompt(null)}>Get rid of it</button>
    </>
  );
};

const TestHarness: React.FunctionComponent = () => {
  return (
    <PromptDisplayBoundary>
      <p>Here be errors...</p>
      <hr />
      <UsingPromptHook />
    </PromptDisplayBoundary>
  );
};

storiesOf("Prompt", module).add("PromptDisplay", () => <TestHarness />);
