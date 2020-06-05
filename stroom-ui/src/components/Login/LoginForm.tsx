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

import * as React from "react";
import { ChangeEventHandler } from "react";
import { NavLink } from "react-router-dom";
import { Credentials } from "components/authentication/types";
import useForm from "react-hook-form";
import { Button, Form, Icon, Input } from "antd";
import { OptionalRequiredFieldMessage } from "../FormComponents/FormComponents";
import LogoPage from "../Layout/LogoPage";
import FormContainer from "../Layout/FormContainer";

interface FormData {
  email: string;
  password: string;
}

export const InputContainer: React.FunctionComponent<{
  label: string;
  children: any;
  error: boolean;
}> = ({ label, children, error }) => {
  return (
    <div className="Login__input-container">
      <div className="Login__label">{label}:</div>
      {children}
      <OptionalRequiredFieldMessage visible={error}/>
    </div>
  );
};


export const PasswordInput: React.FunctionComponent<{
  name: string;
  placeholder: string;
  onChange?: ChangeEventHandler<HTMLInputElement>;
}> = ({ name, placeholder, onChange }) => {
  return (
    <Input.Password
      name={name}
      placeholder={placeholder}
      prefix={
        <Icon type="lock" style={{ color: "rgba(0,0,0,.25)" }}/>
      }
      onChange={onChange}
    />
  );
};


const LoginForm: React.FunctionComponent<{
  onSubmit: (credentials: Credentials) => void;
  isSubmitting: boolean;
  allowPasswordResets?: boolean;
}> = ({ onSubmit, allowPasswordResets, isSubmitting }) => {
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
      password: "",
    },
    mode: "onChange",
  });

  React.useEffect(() => {
    register({ name: "email", type: "custom" }, { required: true });
    register({ name: "password", type: "custom" }, { required: true });
  }, [register]);

  // const { email, password } = getValues();

  const disableSubmit = isSubmitting;//email === "" || password === "";

  const handleInputChange = async (
    name: "email" | "password",
    value: string,
  ) => {
    setValue(name, value);
    await triggerValidation({ name });
  };

  return (
    <LogoPage>
      <FormContainer>
        <Form onSubmit={handleSubmit(onSubmit)}>
          <div className="Login__content">
            <div className="Login__icon-container">
              <img
                src={require("../../images/infinity_logo.svg")}
                alt="Stroom logo"
              />
            </div>
            <InputContainer label="Username" error={Boolean(errors.email)}>
              <Input
                placeholder="username or email"
                prefix={
                  <Icon type="user" style={{ color: "rgba(0,0,0,.25)" }}/>
                }
                name="email"
                autoFocus
                onChange={async e => handleInputChange("email", e.target.value)}
              />
            </InputContainer>
            <InputContainer label="Password" error={Boolean(errors.password)}>
              <PasswordInput name="password"
                             placeholder="password"
                             onChange={async e =>
                               handleInputChange("password", e.target.value)
                             }
              />
            </InputContainer>
            <div className="Login__actions page__buttons Button__container">
              <Button
                className="Login__login-button"
                type="primary"
                loading={isSubmitting}
                disabled={disableSubmit}
                htmlType="submit"
                ref={register}
              >
                Login
              </Button>
            </div>

            {allowPasswordResets ? (
              <NavLink
                className="Login__reset-password"
                to={"/s/resetPasswordRequest"}
              >
                Forgot password?
              </NavLink>
            ) : (
              undefined
            )}

          </div>
        </Form>
      </FormContainer>
    </LogoPage>
  );
};

export default LoginForm;
