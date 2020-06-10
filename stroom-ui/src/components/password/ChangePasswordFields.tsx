/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

// import { ErrorMessage, Field, Form, Formik } from "formik";
import * as React from "react";
// import Button from "components/Button";
// import { Button, Form, Input } from "antd";
// import { hasAnyProps } from "lib/lang";
// import { InputContainer } from "../SignIn/SignInForm";
// import { Icon, Input } from "antd";
import useForm from "react-hook-form";
import { ChangePasswordRequest } from "../Oldauthentication/types";
import Button from "../Button/Button";

interface FormData {
  email: string;
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

const ChangePasswordFields: React.FunctionComponent<{
  email?: string;
  redirectUri?: string;
  showOldPasswordField: boolean;
  onSubmit: (request: ChangePasswordRequest) => void;
  isSubmitting: boolean;
}> = ({ email, redirectUri, showOldPasswordField, onSubmit, isSubmitting }) => {
  // const ChangePasswordFields = ({
  //                                 email,
  //                                 redirectUri,
  //                                 showOldPasswordField,
  //                                 onSubmit,
  //                                 onValidate,
  //                               }: {
  //   email?: string;
  //   redirectUri?: string;
  //   showOldPasswordField: boolean;
  //   onSubmit: Function;
  //   onValidate: (formData: FormData) => Promise<string>;
  // }) => {
  const {
    triggerValidation,
    setValue,
    register,
    handleSubmit,
    // getValues,
    errors,
  } = useForm<FormData>({
    defaultValues: {
      email: "",
      oldPassword: "",
      newPassword: "",
      confirmPassword: "",
    },
    mode: "onChange",
  });

  React.useEffect(() => {
    register({ name: "email", type: "custom" }, { required: true });
    register({ name: "oldPassword", type: "custom" }, { required: true });
    register({ name: "newPassword", type: "custom" }, { required: true });
    register({ name: "confirmPassword", type: "custom" }, { required: true });
  }, [register]);

  // const { email, password } = getValues();

  const disableSubmit = isSubmitting; //email === "" || password === "";

  const handleInputChange = async (
    name: "oldPassword" | "newPassword" | "confirmPassword",
    value: string,
  ) => {
    setValue(name, value);
    await triggerValidation({ name });
  };

  // return (
  // <Formik
  //   enableReinitialize={true}
  //   initialValues={{
  //     oldPassword: "",
  //     newPassword: "",
  //     verifyPassword: "",
  //     email: email || "",
  //     redirectUri: redirectUri || "",
  //   }}
  //   onSubmit={values => {
  //     onSubmit(values);
  //   }}
  //   validate={({ oldPassword, newPassword, email, verifyPassword }) =>
  //     onValidate(oldPassword, newPassword, verifyPassword, email)
  //   }
  // >
  //   {({ errors, touched }) => {
  //     const isPristine = !hasAnyProps(touched);
  //     const hasErrors = hasAnyProps(errors);
  return (
    <form className="ChangePassword-form" onSubmit={handleSubmit(onSubmit)}>
      <div style={{ display: "none" }}>
        <input
          className="redirectUri-field"
          name="redirectUri"
          type="hidden"
          value={redirectUri}
        />
        <input className="email-field" name="email" type="hidden" />
        value={email}
      </div>


      {/*<div className="ChangePassword-controls">*/}
      {/*  <div className="ChangePassword-actions">*/}
      <div className="SignIn__actions page__buttons Button__container">
        <Button
          className="SignIn__button"
          action="primary"
          // loading={isSubmitting}
          disabled={disableSubmit}
          // htmlType="submit"
          icon="save"
          // ref={register}
        >
          Change Password
        </Button>
      </div>

      {/*<Button*/}
      {/*  disabled={isPristine || hasErrors}*/}
      {/*  action="primary"*/}
      {/*  appearance="contained"*/}
      {/*  type="submit"*/}
      {/*  icon="save"*/}
      {/*  text="Change password"*/}
      {/*/>*/}
      {/*</div>*/}
      {/*  </div>*/}
      {/*</div>
            {/*</div>*/}
    </form>
    //     );
    //   }}
    // </Formik>
  );
};

export default ChangePasswordFields;
