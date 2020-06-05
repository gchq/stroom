// This is based on code from here:
// https://www.digitalocean.com/community/tutorials/how-to-build-a-password-strength-meter-in-react

import * as React from "react";
import { FunctionComponent, useState } from "react";

import FormField, { FormFieldState } from "./FormField";
import EmailField from "./EmailField";
import PasswordField from "./PasswordField";
import LogoPage from "../Layout/LogoPage";
import FormContainer from "../Layout/FormContainer";

export interface JoinFormState {
  fullname: boolean;
  email: boolean;
  password: boolean;
}

const JoinForm: FunctionComponent = () => {
  // initialize state to hold validity of form fields
  const [state, setState] = useState<JoinFormState>({
    fullname: false,
    email: false,
    password: false,
  });

  // higher-order function that returns a state change watch function
  // sets the corresponding state property to true if the form field has no errors
  const fieldStateChanged = (field: string) => (s: FormFieldState) => {
    const newState: JoinFormState = {
      ...state,
      [field]: s.errors.length === 0,
    };
    setState(newState);
  };

  // state change watch functions for each field
  const emailChanged = fieldStateChanged("email");
  const fullnameChanged = fieldStateChanged("fullname");
  const passwordChanged = fieldStateChanged("password");

  const { fullname, email, password } = state;
  const formValidated = fullname && email && password;

  // validation function for the fullname
  // ensures that fullname contains at least two names separated with a space
  const validateFullname = (value: string) => {
    const regex = /^[a-z]{2,}(\s[a-z]{2,})+$/i;
    if (!regex.test(value)) throw new Error("Fullname is invalid");
  };

  return (
    <LogoPage>
      <FormContainer>
        <form action="/" method="POST" noValidate>
          <div className="JoinForm__content">
            <div className="d-flex flex-row justify-content-between align-items-center px-3 mb-5">
              <legend className="form-label mb-0">Support Team</legend>
              {/** Show the form button only if all fields are valid **/}
              {formValidated && (
                <button
                  type="button"
                  className="btn btn-primary text-uppercase px-3 py-2"
                >
                  Join
                </button>
              )}
            </div>

            <div className="py-5 border-gray border-top border-bottom">
              {/** Render the fullname form field passing the name validation fn **/}
              <FormField
                type="text"
                fieldId="fullname"
                label="Full Name"
                placeholder="Enter Full Name"
                validator={validateFullname}
                onStateChanged={fullnameChanged}
                required
              />

              {/** Render the email field component **/}
              <EmailField
                fieldId="email"
                label="Email"
                placeholder="Enter Email Address"
                onStateChanged={emailChanged}
                required
              />

              {/** Render the password field component using thresholdLength of 7 and minStrength of 3 **/}
              <PasswordField
                fieldId="password"
                label="Password"
                placeholder="Enter Password"
                onStateChanged={passwordChanged}
                thresholdLength={7}
                minStrength={3}
                required
              />
            </div>
          </div>
        </form>
      </FormContainer>
    </LogoPage>
  );
};

export default JoinForm;
