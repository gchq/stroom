// This is based on code from here:
// https://www.digitalocean.com/community/tutorials/how-to-build-a-password-strength-meter-in-react

import * as React from "react";

import LogoPage from "../Layout/LogoPage";
import FormContainer from "../Layout/FormContainer";
import useForm from "react-hook-form";
import { Credentials } from "../Oldauthentication/types";
import { Button } from "antd";
import { NavLink } from "react-router-dom";

interface FormData {
  password: string;
}

const CurrentPasswordForm: React.FunctionComponent<{
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
      password: "",
    },
    mode: "onChange",
  });

  React.useEffect(() => {
    register({ name: "password", type: "custom" }, { required: true });
  }, [register]);

  // const { email, password } = getValues();

  const disableSubmit = isSubmitting; //email === "" || password === "";

  return (
    <LogoPage>
      <FormContainer>
        <form onSubmit={handleSubmit(onSubmit)}>
          <div className="JoinForm__content">
            <div className="d-flex flex-row justify-content-between align-items-center mb-3">
              <legend className="form-label mb-0">
                Enter Current Password
              </legend>
            </div>

            {/*<PasswordField*/}
            {/*  name="password"*/}
            {/*  label="Password"*/}
            {/*  placeholder="Enter Password"*/}
            {/*  className="no-icon-padding right-icon-padding hide-background-image"*/}
            {/*  validator={fieldRequired}*/}
            {/*  onStateChanged={async e => handleInputChange("password", e.value)}*/}
            {/*/>*/}

            <div className="SignIn__actions page__buttons Button__container">
              <Button
                className="SignIn__button"
                type="primary"
                loading={isSubmitting}
                disabled={disableSubmit}
                htmlType="submit"
              >
                Validate
              </Button>
            </div>

            {allowPasswordResets ? (
              <NavLink
                className="SignIn__reset-password"
                to={"/s/resetPasswordRequest"}
              >
                Forgot password?
              </NavLink>
            ) : (
              undefined
            )}
          </div>
        </form>
      </FormContainer>
    </LogoPage>
  );
};

export default CurrentPasswordForm;
