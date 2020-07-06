import * as React from "react";

import { Formik, FormikProps } from "formik";
import { PasswordField } from "../Form/PasswordField";
import useAuthenticationResource from "./api/useAuthenticationResource";
import { useAlert } from "../AlertDialog/AlertDisplayBoundary";
import * as Yup from "yup";
import { AuthState, ConfirmPasswordRequest } from "./api/types";
import { Alert, AlertType } from "../AlertDialog/AlertDialog";
import { Form, Modal } from "react-bootstrap";
import { Dialog } from "components/Dialog/Dialog";
import { OkCancelButtons, OkCancelProps } from "../Dialog/OkCancelButtons";

export interface FormValues {
  userId: string;
  password: string;
}

export interface AuthStateProps {
  authState: AuthState;
  setAuthState: (state: AuthState) => any;
}

export const ConfirmCurrentPasswordForm: React.FunctionComponent<
  FormikProps<FormValues> & OkCancelProps
> = ({
  values,
  errors,
  touched,
  setFieldTouched,
  handleChange,
  handleBlur,
  handleSubmit,
  isSubmitting,
  onCancel,
  cancelClicked,
}) => (
  <Form noValidate={true} onSubmit={handleSubmit}>
    <Modal.Header closeButton={false}>
      <Modal.Title id="contained-modal-title-vcenter">
        Enter Your Current Password
      </Modal.Title>
    </Modal.Header>
    <Modal.Body>
      <input
        type="text"
        id="userId"
        value={values.userId}
        onChange={handleChange}
        onBlur={handleBlur}
        autoComplete="username"
        hidden={true}
      />
      <Form.Row>
        <PasswordField
          controlId="password"
          label="Current Password"
          placeholder="Enter Your Current Password"
          value={values.password}
          error={errors.password}
          onChange={handleChange}
          onBlur={handleBlur}
          touched={touched.password}
          setFieldTouched={setFieldTouched}
          autoComplete="current-password"
          autoFocus={true}
        />
      </Form.Row>
    </Modal.Body>
    <Modal.Footer>
      <OkCancelButtons
        onOk={() => undefined}
        onCancel={onCancel}
        okClicked={isSubmitting}
        cancelClicked={cancelClicked}
      />
    </Modal.Footer>
  </Form>
);

const ConfirmCurrentPasswordFormik: React.FunctionComponent<{
  userId: string;
  onClose: (userId: string, password: string) => void;
}> = ({ userId, onClose, children }) => {
  const { confirmPassword } = useAuthenticationResource();
  const { alert } = useAlert();

  const passwordSchema = Yup.string()
    .label("Password")
    .required("Password is required");

  const validationSchema = Yup.object().shape({
    password: passwordSchema,
  });

  const onCancel = () => {
    onClose(null, null);
  };

  return (
    <Formik
      initialValues={{ userId, password: "" }}
      validationSchema={validationSchema}
      onSubmit={(values, actions) => {
        const request: ConfirmPasswordRequest = {
          password: values.password,
        };

        confirmPassword(request).then((response) => {
          if (!response) {
            actions.setSubmitting(false);
          } else if (response.valid) {
            onClose(values.userId, values.password);
          } else {
            actions.setSubmitting(false);
            const error: Alert = {
              type: AlertType.ERROR,
              title: "Error",
              message: response.message,
            };
            alert(error);
          }
        });
      }}
    >
      {(props) => (
        <ConfirmCurrentPasswordForm onCancel={onCancel} {...props}>
          {children}
        </ConfirmCurrentPasswordForm>
      )}
    </Formik>
  );
};

export const ConfirmCurrentPassword: React.FunctionComponent<{
  userId: string;
  onClose: (userId: string, password: string) => void;
}> = (props) => {
  return (
    <Dialog>
      <ConfirmCurrentPasswordFormik {...props} />
    </Dialog>
  );
};
