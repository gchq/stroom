import { action } from "@storybook/addon-actions";
import { storiesOf } from "@storybook/react";
import * as React from "react";
import SignInForm from "./SignInForm";
import { addThemedStories } from "../../testing/storybook/themedStoryGenerator";

const stories = storiesOf("Sign In", module);
addThemedStories(stories, "simplest", () => (
  <SignInForm onSubmit={action("onSubmit")} isSubmitting={false} />
));
addThemedStories(stories, "allow password resets", () => (
  <SignInForm
    allowPasswordResets={true}
    onSubmit={action("onSubmit")}
    isSubmitting={false}
  />
));
addThemedStories(stories, "disallow password resets", () => (
  <SignInForm
    allowPasswordResets={false}
    onSubmit={action("onSubmit")}
    isSubmitting={false}
  />
));
