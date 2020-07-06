import { storiesOf } from "@storybook/react";
import * as React from "react";
import { ChangePasswordForm } from "./ChangePassword";
import { Formik } from "formik";
import * as Yup from "yup";
import { Dialog } from "components/Dialog/Dialog";
import { useState } from "react";
import zxcvbn from "zxcvbn";

const FormikWrapper: React.FunctionComponent = () => {
  const [strength, setStrength] = useState(0);
  let currentStrength = strength;

  const minStrength = 3;
  const thresholdLength = 7;

  const passwordSchema = Yup.string()
    .label("Password")
    .required("Password is required")
    .min(thresholdLength, "Password is short")
    .test(
      "password-strength",
      "Password is weak",
      () => currentStrength > minStrength,
    );

  const confirmPasswordSchema = Yup.string()
    .label("Confirm Password")
    .required("Required")
    .test("password-match", "Passwords must match", function (value) {
      const { resolve } = this;
      const ref = Yup.ref("password");
      return value === resolve(ref);
    });

  const validationSchema = Yup.object().shape({
    password: passwordSchema,
    confirmPassword: confirmPasswordSchema,
  });

  return (
    <Formik
      initialValues={{ userId: "", password: "", confirmPassword: "" }}
      validationSchema={validationSchema}
      onSubmit={(values, actions) => {
        setTimeout(() => {
          alert(JSON.stringify(values, null, 2));
          actions.setSubmitting(false);
        }, 1000);
      }}
    >
      {(props) => {
        const handler = (e: React.ChangeEvent<HTMLInputElement>) => {
          if (e.target.id === "password") {
            const score = zxcvbn(e.target.value).score;
            setStrength(score);
            currentStrength = score;
          }
          props.handleChange(e);
        };

        return (
          <ChangePasswordForm
            {...props}
            strength={strength}
            minStrength={minStrength}
            thresholdLength={thresholdLength}
            handleChange={handler}
          />
        );
      }}
    </Formik>
  );
};

export const ChangePasswordDialog: React.FunctionComponent = () => {
  return (
    <Dialog>
      <FormikWrapper />
    </Dialog>
  );
};

const TestHarness: React.FunctionComponent = () => {
  return <ChangePasswordDialog />;
};

storiesOf("Authentication", module).add("Change Password", () => (
  <TestHarness />
));
