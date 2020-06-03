import * as React from "react";
import { storiesOf } from "@storybook/react";

import { addThemedStories } from "testing/storybook/themedStoryGenerator";

import Tooltip from "./Tooltip";

const stories = storiesOf("General Purpose", module);

addThemedStories(stories, "Tooltip", () => (
  <Tooltip
    trigger={
      <button onClick={() => console.log("Clicked the tooltip button")}>
        Click Me
      </button>
    }
    content="Click this button, check the console"
  />
));
