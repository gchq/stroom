import { action } from "@storybook/addon-actions";
import { storiesOf } from "@storybook/react";
import * as React from "react";
import ChangePasswordForm from "./ChangePasswordForm";

const onValidate = (
  oldPassword: string,
  password: string,
  verifyPassword: string,
  email: string,
) => {
  action("onValidate");
  return new Promise<string>(() => "");
};

storiesOf("Auth/ChangePassword", module)
  .add("simplest", () => (
    <ChangePasswordForm
      onSubmit={(whatevs: any) => action(whatevs)}
      email="User"
      onValidate={onValidate}
    />
  ))
  .add("confirmChange", () => (
    <ChangePasswordForm
      onSubmit={action("onSubmit")}
      email="User"
      showChangeConfirmation={true}
      onValidate={onValidate}
    />
  ));
