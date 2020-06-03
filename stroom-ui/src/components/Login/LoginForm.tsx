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
import { NavLink } from "react-router-dom";
import { Credentials } from "components/authentication/types";
import useForm from "react-hook-form";
import { Button, Form, Icon, Input } from "antd";
import { RequiredFieldMessage } from "components/FormComponents";
import styled from "styled-components";
import useConfig from "../../startup/config/useConfig";

interface FormData {
  email: string;
  password: string;
}

const InputContainer = styled.div`
  display: flex;
  flex-direction: column;
  height: 4rem;
`;

const StatusContainer = styled.div`
  margin-top: 1rem;
  display: flex;

  a > {
    align-self: flex-end;
  }
`;

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

  const { theme } = useConfig();

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
    <div style={theme} className="content-floating-without-appbar">
      <img
        className="content-logo"
        alt="Stroom logo"
        src={require("../../images/logo.svg")}
      />
      <div className="Login__container">
        <Form onSubmit={handleSubmit(onSubmit)}>
          <div className="Login__content">
            <div className="Login__icon-container">
              <img
                src={require("../../images/infinity_logo.svg")}
                alt="Stroom logo"
              />
            </div>
            <InputContainer>
              <Input
                placeholder="username or email"
                prefix={
                  <Icon type="user" style={{ color: "rgba(0,0,0,.25)" }}/>
                }
                name="email"
                autoFocus
                onChange={async e => handleInputChange("email", e.target.value)}
              />
              {errors.email && <RequiredFieldMessage/>}
            </InputContainer>
            <InputContainer>
              <Input.Password
                name="password"
                placeholder="password"
                prefix={
                  <Icon type="lock" style={{ color: "rgba(0,0,0,.25)" }}/>
                }
                onChange={async e =>
                  handleInputChange("password", e.target.value)
                }
              />
              {errors.password && <RequiredFieldMessage/>}
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

            <StatusContainer>
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
            </StatusContainer>

          </div>
        </Form>
      </div>
    </div>
  );
};

export default LoginForm;
