import * as React from "react";
import { action } from "@storybook/addon-actions";
import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import CreateTokenForm from "./CreateTokenForm";

const stories = storiesOf("Tokens/Create", module);

addThemedStories(stories, () => (
  <CreateTokenForm onSubmit={action("onSubmit")} onBack={action("onBack")} />
));
