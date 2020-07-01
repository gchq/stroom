import { storiesOf } from "@storybook/react";
import * as React from "react";
import { ConfirmCurrentPasswordForm } from "./ConfirmCurrentPassword";
import { Formik } from "formik";
import * as Yup from "yup";
import { CustomModal } from "./FormField";

const FormikWrapper: React.FunctionComponent = () => {
  return (
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
      {(props) => <ConfirmCurrentPasswordForm {...props} />}
    </Formik>
  );
};

export const ConfirmCurrentPasswordDialog: React.FunctionComponent = () => {
  return (
    <CustomModal>
      <FormikWrapper />
    </CustomModal>
  );
};

const TestHarness: React.FunctionComponent = () => {
  return <ConfirmCurrentPasswordDialog />;
};

storiesOf("Authentication", module).add("Confirm Current Password", () => (
  <TestHarness />
));
