import { storiesOf } from "@storybook/react";
import * as React from "react";
import { Page, Form } from "./ConfirmCurrentPasswordForm";
import { Formik } from "formik";
import * as Yup from "yup";

const TestHarness: React.FunctionComponent<{
  allowPasswordResets?: boolean;
}> = ({ allowPasswordResets }) => {
  return (
    <Page allowPasswordResets={allowPasswordResets}>
      <Formik
        initialValues={{ userId: "", password: "" }}
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
        {(props) => <Form {...props} />}
      </Formik>
    </Page>
  );
};

const stories = storiesOf("Authentication", module);
stories.add("Confirm Current Password - allow password resets", () => (
  <TestHarness allowPasswordResets={true} />
));
stories.add("Confirm Current Password - disallow password resets", () => (
  <TestHarness allowPasswordResets={false} />
));
