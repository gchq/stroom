// This is based on code from here:
// https://www.digitalocean.com/community/tutorials/how-to-build-a-password-strength-meter-in-react

import * as React from "react";

import LogoPage from "../Layout/LogoPage";
import FormContainer from "../Layout/FormContainer";
import PasswordField from "../ChangePassword2/PasswordField";
import useForm from "react-hook-form";
import { Credentials } from "../authentication/types";
import { Button } from "antd";
import { NavLink } from "react-router-dom";

interface FormData {
  password: string;
}

const CurrentPassword: React.FunctionComponent<{
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

  const handleInputChange = async (name: "password", value: string) => {
    setValue(name, value);
    await triggerValidation({ name });
  };
  //
  // // initialize state to hold validity of form fields
  // const [state, setState] = useState<JoinFormState>({
  //   fullname: false,
  //   email: false,
  //   password: false,
  // });
  //
  // // higher-order function that returns a state change watch function
  // // sets the corresponding state property to true if the form field has no errors
  // const fieldStateChanged = (field: string) => (s: FormFieldState) => {
  //   const newState: JoinFormState = {
  //     ...state,
  //     [field]: s.errors.length === 0,
  //   };
  //   setState(newState);
  // };
  //
  // // state change watch functions for each field
  // const emailChanged = fieldStateChanged("email");
  // const fullnameChanged = fieldStateChanged("fullname");
  // const passwordChanged = fieldStateChanged("password");
  //
  // const { fullname, email, password } = state;
  // const formValidated = fullname && email && password;
  //
  // // validation function for the fullname
  // // ensures that fullname contains at least two names separated with a space
  // const validateFullname = (label: string, value: string) => {
  //   if (value.length === 0) {
  //     throw new Error(`${label} is required`);
  //   } else {
  //     const regex = /^[a-z]{2,}(\s[a-z]{2,})+$/i;
  //     if (!regex.test(value)) throw new Error(`${label} is invalid`);
  //   }
  // };

  // ensures that field contains characters
  const fieldRequired = (label: string, value: string) => {
    if (value.length === 0) {
      // if required and is empty, add required error to state
      throw new Error(`${label} is required`);
    } else {
      const regex = /^.+$/i;
      if (!regex.test(value)) throw new Error("Field required");
    }
  };

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

            <PasswordField
              fieldId="password"
              label="Password"
              placeholder="Enter Password"
              className="no-icon-padding right-icon-padding hide-background-image"
              validator={fieldRequired}
              onStateChanged={async e => handleInputChange("password", e.value)}
            />

            <div className="SignIn__actions page__buttons Button__container">
              <Button
                className="SignIn__button"
                type="primary"
                loading={isSubmitting}
                disabled={disableSubmit}
                htmlType="submit"
                ref={register}
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

export default CurrentPassword;
