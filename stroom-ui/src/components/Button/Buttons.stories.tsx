import * as React from "react";
import { storiesOf } from "@storybook/react";
import { action } from "@storybook/addon-actions";

import Button from "./Button";

const clickAction = action("Button Clicked");

storiesOf("Button", module).add("with text", () => (
  <div>
    Hello World
    <Button onClick={clickAction} text="Click Me" />
  </div>
));
