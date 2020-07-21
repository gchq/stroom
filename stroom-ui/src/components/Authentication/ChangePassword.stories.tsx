import { storiesOf } from "@storybook/react";
import * as React from "react";
import { ChangePasswordDialog, ChangePasswordPage } from "./ChangePassword";
import Background from "../Layout/Background";
import BackgroundLogo from "../Layout/BackgroundLogo";

storiesOf("Authentication", module)
  .add("Change Password Dialog", () => (
    <Background>
      <BackgroundLogo>
        <ChangePasswordDialog
          initialValues={{
            userId: "",
            password: "",
            confirmPassword: "",
          }}
          passwordPolicyConfig={{
            minimumPasswordLength: 7,
            minimumPasswordStrength: 3,
          }}
          onSubmit={() => undefined}
          onClose={() => undefined}
        />
      </BackgroundLogo>
    </Background>
  ))
  .add("Change Password Page", () => (
    <Background>
      <BackgroundLogo>
        <ChangePasswordPage
          initialValues={{
            userId: "",
            password: "",
            confirmPassword: "",
          }}
          passwordPolicyConfig={{
            minimumPasswordLength: 7,
            minimumPasswordStrength: 3,
          }}
          onSubmit={() => undefined}
          onClose={() => undefined}
        />
      </BackgroundLogo>
    </Background>
  ));
