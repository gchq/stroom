import * as React from "react";
import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import JsonDebug from "testing/JsonDebug";
import NewUserForm, { useThisForm } from "./NewUserForm";

const TestHarness: React.FunctionComponent = () => {
  const { value, componentProps } = useThisForm();

  return (
    <div>
      <NewUserForm {...componentProps} />
      <JsonDebug value={value} />
    </div>
  );
};

const stories = storiesOf(
  "Sections/Authorisation Manager/New User",
  module,
);

addThemedStories(stories, "Form", () => <TestHarness />);
