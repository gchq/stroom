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
import useConfig from "../../startup/config/useConfig";
import { OptionalRequiredFieldMessage } from "../FormComponents/FormComponents";

interface FormData {
  email: string;
  password: string;
}

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
            <div className="Login__input-container">
              <div className="Login__label">Username:</div>
              <Input
                placeholder="username or email"
                prefix={
                  <Icon type="user" style={{ color: "rgba(0,0,0,.25)" }}/>
                }
                name="email"
                autoFocus
                onChange={async e => handleInputChange("email", e.target.value)}
              />
              {/*<ErrorMessage message={errors.email ? errors.email.message : ""} />*/}
              {/*{errors.email && <RequiredFieldMessage/>}*/}

              <OptionalRequiredFieldMessage visible={Boolean(errors.email)}/>
            </div>
            <div className="Login__input-container">
              <div className="Login__label">Password:</div>
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

              <OptionalRequiredFieldMessage visible={Boolean(errors.password)}/>

              {/*<ValidationMessage>{errors.password ? errors.password.message : ""}</ValidationMessage>*/}
            </div>

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
      </div>
    </div>
  );
};

export default LoginForm;
