import { action } from "@storybook/addon-actions";
import { storiesOf } from "@storybook/react";
import * as React from "react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import NewUserForm from "./NewUserForm";

const onValidate = (
  password: string,
  verifyPassword: string,
  email: string,
) => {
  action("onValidate");
  return new Promise<string>(() => "wat");
};

const stories = storiesOf(`Users/New User Form`, module);
stories.add("Test", () => {
  return (
    <NewUserForm
      onSubmit={() => console.log("TODO: onsubmit")}
      onBack={() => console.log("TODO: onBack")}
      onCancel={() => console.log("TODO: onCancel")}
      onValidate={onValidate}
      account={undefined}
    />
  );
});
