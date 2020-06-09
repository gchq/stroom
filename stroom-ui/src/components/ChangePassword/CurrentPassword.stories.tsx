import { storiesOf } from "@storybook/react";
import * as React from "react";
import CurrentPassword from "./CurrentPassword";
import { action } from "@storybook/addon-actions";
import { addThemedStories } from "../../testing/storybook/themedStoryGenerator";

const stories = storiesOf("CurrentPassword", module);
addThemedStories(stories, "simplest", () => (
  <CurrentPassword onSubmit={action("onSubmit")} isSubmitting={false} />
));
addThemedStories(stories, "allow password resets", () => (
  <CurrentPassword
    allowPasswordResets={true}
    onSubmit={action("onSubmit")}
    isSubmitting={false}
  />
));
addThemedStories(stories, "disallow password resets", () => (
  <CurrentPassword
    allowPasswordResets={false}
    onSubmit={action("onSubmit")}
    isSubmitting={false}
  />
));
