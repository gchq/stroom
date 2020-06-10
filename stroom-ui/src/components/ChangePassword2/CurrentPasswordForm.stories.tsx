import { storiesOf } from "@storybook/react";
import * as React from "react";
import CurrentPasswordForm from "./CurrentPasswordForm";
import { action } from "@storybook/addon-actions";
import { addThemedStories } from "../../testing/storybook/themedStoryGenerator";

const stories = storiesOf("CurrentPasswordForm", module);
addThemedStories(stories, "simplest", () => (
  <CurrentPasswordForm onSubmit={action("onSubmit")} isSubmitting={false} />
));
addThemedStories(stories, "allow password resets", () => (
  <CurrentPasswordForm
    allowPasswordResets={true}
    onSubmit={action("onSubmit")}
    isSubmitting={false}
  />
));
addThemedStories(stories, "disallow password resets", () => (
  <CurrentPasswordForm
    allowPasswordResets={false}
    onSubmit={action("onSubmit")}
    isSubmitting={false}
  />
));
