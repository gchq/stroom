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
import { Button, Input, Icon, Form } from "antd";
import { RequiredFieldMessage } from "components/FormComponents";
import styled from "styled-components";

interface FormData {
  email: string;
  password: string;
}

const InputContainer = styled.div`
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 5.5em;
`;

const StatusContainer = styled.div`
  margin-top: 1em;
  display: flex;
  justify-content: space-between;

  a > {
    align-self: flex-end;
  }
`;

const LoginForm: React.FunctionComponent<{
  onSubmit: (credentials: Credentials) => void;
  isSubmitting: boolean;
  loginResultMessage?: string;
  allowPasswordResets?: boolean;
}> = ({ onSubmit, allowPasswordResets, loginResultMessage, isSubmitting }) => {
  const {
    triggerValidation,
    setValue,
    register,
    handleSubmit,
    getValues,
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

  const { email, password } = getValues();

  const disableSubmit = email === "" || password === "";

  const handleInputChange = async (
    name: "email" | "password",
    value: string,
  ) => {
    setValue(name, value);
    await triggerValidation({ name });
  };

  return (
    <div className="content-floating-without-appbar">
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
                  <Icon type="user" style={{ color: "rgba(0,0,0,.25)" }} />
                }
                name="email"
                autoFocus
                onChange={async e => handleInputChange("email", e.target.value)}
              />
              {errors.email && <RequiredFieldMessage />}
              <Input.Password
                name="password"
                placeholder="password"
                prefix={
                  <Icon type="lock" style={{ color: "rgba(0,0,0,.25)" }} />
                }
                onChange={async e =>
                  handleInputChange("password", e.target.value)
                }
              />
              {errors.password && <RequiredFieldMessage />}
            </InputContainer>
            <StatusContainer>
              {loginResultMessage ? (
                <div className="validation-error">{loginResultMessage}</div>
              ) : (
                <div />
              )}
              {allowPasswordResets ? (
                <NavLink
                  className="Login__reset-password"
                  to={"/resetPasswordRequest"}
                >
                  Reset password?
                </NavLink>
              ) : (
                undefined
              )}
            </StatusContainer>

            <div className="Login__actions page__buttons Button__container">
              <Button
                type="primary"
                loading={isSubmitting}
                disabled={disableSubmit}
                htmlType="submit"
                ref={register}
              >
                Submit
              </Button>
            </div>
          </div>
        </Form>
      </div>
    </div>
  );
};

export default LoginForm;
