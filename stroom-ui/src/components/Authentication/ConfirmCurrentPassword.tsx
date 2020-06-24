import * as React from "react";

import { Formik, FormikProps } from "formik";
import PasswordField from "./PasswordField";
import useAuthenticationApi from "./api/useAuthenticationApi";
import { useAlert } from "../AlertDialog/AlertDisplayBoundary";
import * as Yup from "yup";
import { AuthState, ConfirmPasswordRequest } from "./api/types";
import { Alert, AlertType } from "../AlertDialog/AlertDialog";
import { Ref } from "react";
import { Form, Modal } from "react-bootstrap";
import { CustomModal } from "./FormField";
import OkCancelButtons from "./OkCancelButtons";

export interface FormValues {
  userId: string;
  password: string;
}

export interface AuthStateProps {
  authState: AuthState;
  setAuthState: (state: AuthState) => any;
}

export interface OkCancelProps {
  onOk: () => void;
  onCancel: () => void;
  okClicked: boolean;
  cancelClicked: boolean;
  ref?: Ref<HTMLButtonElement>;
}

export const ConfirmCurrentPasswordForm: React.FunctionComponent<FormikProps<
  FormValues
>> = ({
  values,
  errors,
  touched,
  setFieldTouched,
  handleChange,
  handleBlur,
  handleSubmit,
  isSubmitting,
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
          controlId="validationFormik02"
          label="Current Password"
          name="password"
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
        onCancel={() => undefined}
        okClicked={isSubmitting}
        cancelClicked={false}
      />
    </Modal.Footer>
  </Form>
);

const ConfirmCurrentPasswordFormik: React.FunctionComponent<AuthStateProps> = ({
  authState,
  setAuthState,
  children,
}) => {
  const { confirmPassword } = useAuthenticationApi();
  const { alert } = useAlert();

  const passwordSchema = Yup.string()
    .label("Password")
    .required("Password is required");

  const validationSchema = Yup.object().shape({
    password: passwordSchema,
  });

  return (
    <Formik
      initialValues={{ userId: authState.userId, password: "" }}
      validationSchema={validationSchema}
      onSubmit={(values, actions) => {
        const request: ConfirmPasswordRequest = {
          password: values.password,
        };

        confirmPassword(request).then((response) => {
          if (!response) {
            actions.setSubmitting(false);
          } else if (response.valid) {
            setAuthState({
              ...authState,
              userId: values.userId,
              currentPassword: values.password,
              showConfirmPassword: false,
              showChangePassword: true,
            });
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
        <ConfirmCurrentPasswordForm {...props}>
          {children}
        </ConfirmCurrentPasswordForm>
      )}
    </Formik>
  );
};

export const ConfirmCurrentPassword: React.FunctionComponent<AuthStateProps> = (
  props,
) => {
  return (
    <CustomModal
      show={props.authState.showConfirmPassword}
      centered={true}
      aria-labelledby="contained-modal-title-vcenter"
    >
      <ConfirmCurrentPasswordFormik {...props} />
    </CustomModal>
  );
};
