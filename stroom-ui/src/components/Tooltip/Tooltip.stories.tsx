import * as React from "react";
import { storiesOf } from "@storybook/react";
import StroomDecorator from "../../lib/storybook/StroomDecorator";
import { addThemedStories } from "../../lib/themedStoryGenerator";

import Tooltip from "./Tooltip";

const stories = storiesOf("Tooltip", module).addDecorator(StroomDecorator);

addThemedStories(
  stories,
  <Tooltip
    trigger={
      <button onClick={() => console.log("Clicked the tooltip button")}>
        Click Me
      </button>
    }
    content="Click this button, check the console"
  />
);
