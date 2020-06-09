import { storiesOf } from "@storybook/react";
import * as React from "react";
import SignInForm from "./SignInForm";
import { addThemedStories } from "../../testing/storybook/themedStoryGenerator";

const TestHarness: React.FunctionComponent<{
  allowPasswordResets?: boolean;
}> = ({ allowPasswordResets }) => {
  const [submitting, setSubmitting] = React.useState<boolean>(false);

  return (
    <SignInForm
      allowPasswordResets={allowPasswordResets}
      onSubmit={credentials => setSubmitting(true)}
      isSubmitting={submitting}
    />
  );
};

const stories = storiesOf("Sign In", module);
addThemedStories(stories, "simplest", () => <TestHarness />);
addThemedStories(stories, "allow password resets", () => (
  <TestHarness allowPasswordResets={true} />
));
addThemedStories(stories, "disallow password resets", () => (
  <TestHarness allowPasswordResets={false} />
));
