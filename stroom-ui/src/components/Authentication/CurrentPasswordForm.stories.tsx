import { storiesOf } from "@storybook/react";
import * as React from "react";
import CurrentPasswordForm from "./CurrentPasswordForm";
import { addThemedStories } from "../../testing/storybook/themedStoryGenerator";
import { Formik } from "formik";
import * as Yup from "yup";

const TestHarness: React.FunctionComponent<{
  allowPasswordResets?: boolean;
}> = ({ allowPasswordResets }) => {
  return (
    <Formik
      initialValues={{ email: "", password: "" }}
      validationSchema={Yup.object().shape({
        password: Yup.string().required("Password is required"),
      })}
      onSubmit={(values, actions) => {
        setTimeout(() => {
          alert(JSON.stringify(values, null, 2));
          actions.setSubmitting(false);
        }, 1000);
      }}
    >
      {props => (
        <CurrentPasswordForm
          allowPasswordResets={allowPasswordResets}
          {...props}
        />
      )}
    </Formik>
  );
};

const stories = storiesOf("Authentication", module);
addThemedStories(stories, "Current Password - allow password resets", () => (
  <TestHarness allowPasswordResets={true} />
));
addThemedStories(stories, "Current Password - disallow password resets", () => (
  <TestHarness allowPasswordResets={false} />
));
