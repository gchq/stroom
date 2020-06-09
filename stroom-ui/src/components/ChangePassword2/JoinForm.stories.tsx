import { storiesOf } from "@storybook/react";
import * as React from "react";
import JoinForm from "./JoinForm";
import { Formik } from "formik";
import * as Yup from "yup";
import * as zxcvbn from "zxcvbn";
import { addThemedStories } from "../../testing/storybook/themedStoryGenerator";

const TestHarness: React.FunctionComponent = () => {
  const minStrength = 3;
  const thresholdLength = 7;

  return (
    <Formik
      initialValues={{ fullname: "", email: "", password: "" }}
      validationSchema={Yup.object().shape({
        fullname: Yup.string()
          .required("Full name is required")
          .test("fullname-pattern", "Full name is invalid", value => {
            const regex = /^[a-z]{2,}(\s[a-z]{2,})+$/i;
            return regex.test(value);
          }),
        email: Yup.string()
          .email("Email not valid")
          .required("Email is required"),
        password: Yup.string()
          .required("Password is required")
          .min(thresholdLength, "Password is short"),
          // .test(
          //   "password-strength",
          //   "Password is weak",
          //   value => zxcvbn(value).score >= minStrength,
          // ),
      })}
      onSubmit={(values, actions) => {
        setTimeout(() => {
          alert(JSON.stringify(values, null, 2));
          actions.setSubmitting(false);
        }, 1000);
      }}
    >
      {props => (
        <JoinForm
          minStrength={minStrength}
          thresholdLength={thresholdLength}
          {...props}
        />
      )}
    </Formik>
  );
};

const stories = storiesOf("Join Form", module);
addThemedStories(stories, "Join Form", () => <TestHarness />);
