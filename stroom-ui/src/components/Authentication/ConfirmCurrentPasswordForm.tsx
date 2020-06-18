import * as React from "react";

import LogoPage from "../Layout/LogoPage";
import FormContainer from "../Layout/FormContainer";
import { Button } from "antd";
import { NavLink, useHistory } from "react-router-dom";
import { Formik, FormikProps } from "formik";
import PasswordField from "./PasswordField";
import useAuthenticationApi from "./api/useAuthenticationApi";
import { useAlert } from "../AlertDialog/AlertDisplayBoundary";
import * as Yup from "yup";
import { ConfirmPasswordRequest } from "./api/types";
import { Alert, AlertType } from "../AlertDialog/AlertDialog";
import Cookies from "cookies-js";

export interface FormValues {
  userId: string;
  password: string;
}

export interface PageProps {
  allowPasswordResets?: boolean;
}

export const Form: React.FunctionComponent<FormikProps<FormValues>> = ({
  values,
  errors,
  touched,
  setFieldTouched,
  handleChange,
  handleBlur,
  handleSubmit,
  isSubmitting,
}) => (
  <form onSubmit={handleSubmit}>
    <input
      type="text"
      id="userId"
      value={values.userId}
      onChange={handleChange}
      onBlur={handleBlur}
      autoComplete="username"
      hidden={true}
    />

    <PasswordField
      name="password"
      label="Password"
      autoComplete="current-password"
      placeholder="Enter Password"
      className="no-icon-padding right-icon-padding hide-background-image"
      onChange={handleChange}
      onBlur={handleBlur}
      value={values.password}
      error={errors.password}
      touched={touched.password}
      setFieldTouched={setFieldTouched}
    />

    <div className="SignIn__actions page__buttons Button__container">
      <Button
        className="SignIn__button"
        type="primary"
        loading={isSubmitting}
        htmlType="submit"
      >
        Validate
      </Button>
    </div>
  </form>
);

const FormikWrapper: React.FunctionComponent = () => {
  const { confirmPassword } = useAuthenticationApi();
  const { alert } = useAlert();

  const passwordSchema = Yup.string()
    .label("Password")
    .required("Password is required");

  const validationSchema = Yup.object().shape({
    password: passwordSchema,
  });

  const history = useHistory();

  return (
    <Formik
      initialValues={{ userId: Cookies.get("userId"), password: "" }}
      validationSchema={validationSchema}
      onSubmit={(values, actions) => {
        const request: ConfirmPasswordRequest = {
          password: values.password,
        };

        confirmPassword(request).then((response) => {
          if (!response) {
            actions.setSubmitting(false);
          } else if (response.valid) {
            history.push(response.redirectUri);
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
      {(props) => <Form {...props} />}
    </Formik>
  );
};

export const Page: React.FunctionComponent<PageProps> = ({
  allowPasswordResets,
  children,
}) => (
  <LogoPage>
    <FormContainer>
      <div className="JoinForm__content">
        <div className="d-flex flex-row justify-content-between align-items-center mb-3">
          <legend className="form-label mb-0">Enter Current Password</legend>
        </div>

        {children}

        {allowPasswordResets ? (
          <NavLink
            className="SignIn__reset-password"
            to={"/s/resetPasswordRequest"}
          >
            Forgot password?
          </NavLink>
        ) : undefined}
      </div>
    </FormContainer>
  </LogoPage>
);

const ConfirmCurrentPasswordForm: React.FunctionComponent = () => (
  <Page>
    <FormikWrapper />
  </Page>
);

export default ConfirmCurrentPasswordForm;
