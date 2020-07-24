import * as React from "react";
import { action } from "@storybook/addon-actions";
import { storiesOf } from "@storybook/react";
import CreateTokenForm from "./CreateTokenForm";

storiesOf("Tokens", module).add("Create", () => (
  <CreateTokenForm onSubmit={action("onSubmit")} onBack={action("onBack")} />
));
