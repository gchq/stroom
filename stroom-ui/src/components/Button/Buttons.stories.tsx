import * as React from "react";
import { storiesOf } from "@storybook/react";
import { withInfo } from "@storybook/addon-info";
import { action } from "@storybook/addon-actions";

import Button from "./Button";

const clickAction = action("Button Clicked");

storiesOf("Button", module).add(
  "with text",
  withInfo({ inline: true })(() => (
    <Button onClick={clickAction} text="Click Me" />
  ))
);
