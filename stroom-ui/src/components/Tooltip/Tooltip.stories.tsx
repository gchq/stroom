import * as React from "react";
import { storiesOf } from "@storybook/react";

import { addThemedStories } from "testing/storybook/themedStoryGenerator";

import Tooltip from "./Tooltip";

const stories = storiesOf("General Purpose/Tooltip", module);

addThemedStories(stories, () => (
  <Tooltip
    trigger={
      <button onClick={() => console.log("Clicked the tooltip button")}>
        Click Me
      </button>
    }
    content="Click this button, check the console"
  />
));
