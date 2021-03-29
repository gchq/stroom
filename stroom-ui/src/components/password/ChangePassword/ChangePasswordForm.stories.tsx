// import { action } from "@storybook/addon-actions";
import { storiesOf } from "@storybook/react";
import * as React from "react";
import ChangePasswordForm from "./ChangePasswordForm";
// import { ChangePasswordRequest } from "../../Oldauthentication/types";

// const onValidate = (
//   oldPassword: string,
//   password: string,
//   verifyPassword: string,
//   email: string,
// ) => {
//   action("onValidate");
//   return new Promise<ChangePasswordRequest>(() => "");
// };

storiesOf("Auth/ChangePassword", module)
  .add("simplest", () => (
    <ChangePasswordForm
      // onSubmit={(request: ChangePasswordRequest) => action(request)}
      email="User"
      onSubmit={undefined}
      isSubmitting={false}
    />
  ))
  .add("confirmChange", () => (
    <ChangePasswordForm
      // onSubmit={action("onSubmit")}
      email="User"
      onSubmit={undefined}
      isSubmitting={false}
      showChangeConfirmation={true}
      // onValidate={onValidate}
    />
  ));
