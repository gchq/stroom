import { storiesOf } from "@storybook/react";
import * as React from "react";
import { EditAccountForm } from "./EditAccount";
import { Formik } from "formik";
import { Dialog } from "components/Dialog/Dialog";
import { newAccountValidationSchema } from "./validation";

const FormikWrapper: React.FunctionComponent = () => {
  return (
    <Formik
      initialValues={{
        userId: "",
        email: "",
        firstName: "",
        lastName: "",
        comments: "",
      }}
      validationSchema={newAccountValidationSchema}
      onSubmit={(values, actions) => {
        setTimeout(() => {
          alert(JSON.stringify(values, null, 2));
          actions.setSubmitting(false);
        }, 1000);
      }}
    >
      {(props) => {
        return <EditAccountForm {...props} />;
      }}
    </Formik>
  );
};

export const EditAccountDialog: React.FunctionComponent = () => {
  return (
    <Dialog>
      <FormikWrapper />
    </Dialog>
  );
};

const TestHarness: React.FunctionComponent = () => {
  return <EditAccountDialog />;
};

storiesOf("Account", module).add("Edit Account", () => <TestHarness />);
