import { action } from "@storybook/addon-actions";
import { storiesOf } from "@storybook/react";
import * as React from "react";
import ConfirmPasswordResetEmail from "./ConfirmPasswordResetEmail";

const stories = storiesOf("Auth/ConfirmPasswordResetEmail", module);

stories.add("test", () => (
  <ConfirmPasswordResetEmail onBack={action("onBack")} />
));
