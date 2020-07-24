import * as React from "react";
import { storiesOf } from "@storybook/react";

import Tooltip from "./Tooltip";

storiesOf("General Purpose", module).add("Tooltip", () => (
  <Tooltip
    trigger={
      <button onClick={() => console.log("Clicked the tooltip button")}>
        Click Me
      </button>
    }
    content="Click this button, check the console"
  />
));
