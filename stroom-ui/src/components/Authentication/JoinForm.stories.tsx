import { storiesOf } from "@storybook/react";
import * as React from "react";
import { JoinForm } from "./JoinForm";
import { Formik } from "formik";
import * as Yup from "yup";
import { useState } from "react";
import zxcvbn from "zxcvbn";

const TestHarness: React.FunctionComponent = () => {
  const [strength, setStrength] = useState(0);
  let currentStrength = strength;

  const minStrength = 3;
  const thresholdLength = 7;

  const fullNameSchema = Yup.string()
    .required("Full name is required")
    .matches(/^[a-z]{2,}(\s[a-z]{2,})+$/i, "Full name is invalid");
  const emailSchema = Yup.string()
    .email("Email is invalid")
    .required("Email is required");
  const passwordSchema = Yup.string()
    .label("Password")
    .required("Password is required")
    .min(thresholdLength, "Password is short")
    .test(
      "password-strength",
      "Password is weak",
      () => currentStrength > minStrength,
    );

  const validationSchema = Yup.object().shape({
    fullname: fullNameSchema,
    email: emailSchema,
    password: passwordSchema,
  });

  return (
    <Formik
      initialValues={{ fullname: "", email: "", password: "" }}
      validationSchema={validationSchema}
      onSubmit={(values, actions) => {
        setTimeout(() => {
          alert(JSON.stringify(values, null, 2));
          actions.setSubmitting(false);
        }, 1000);
      }}
    >
      {(formikProps) => {
        const handler = (e: React.ChangeEvent<HTMLInputElement>) => {
          if (e.target.id === "password") {
            const score = zxcvbn(e.target.value).score;
            setStrength(score);
            currentStrength = score;
          }
          formikProps.handleChange(e);
        };

        return (
          <JoinForm
            formikProps={{ ...formikProps, handleChange: handler }}
            passwordStrengthProps={{ strength, minStrength, thresholdLength }}
          />
        );
      }}
    </Formik>
  );
};

storiesOf("Authentication", module).add("Join Form", () => <TestHarness />);
