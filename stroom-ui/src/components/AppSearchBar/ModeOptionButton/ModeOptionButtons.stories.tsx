import * as React from "react";

import { storiesOf } from "@storybook/react";

import ModeOptionButtons, { useModeOptionButtons } from "./ModeOptionButtons";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import JsonDebug from "testing/JsonDebug";

const stories = storiesOf("App Search Bar/Mode Option", module);

const TestHarness = () => {
  const { searchMode, componentProps } = useModeOptionButtons();

  return (
    <div>
      <ModeOptionButtons {...componentProps} />
      <JsonDebug value={{ searchMode }} />
    </div>
  );
};

addThemedStories(stories, () => <TestHarness />);
