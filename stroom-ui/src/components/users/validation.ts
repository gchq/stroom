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

import * as Yup from "yup";

export const NewUserValidationSchema = Yup.object().shape({
  email: Yup.string().required("Required"),
  password: Yup.string().required("Required"),
  verifyPassword: Yup.string().required("Required"),
});

export const UserValidationSchema = Yup.object().shape({
  email: Yup.string().required("Required"),
});

interface PasswordValidationErrors {
  password: string;
  oldPassword: string;
  verifyPassword: string;
}

function validateVerifyPassword(
  newPassword: string,
  verifyPassword: string,
): string {
  if (
    newPassword !== undefined &&
    newPassword !== "" &&
    verifyPassword !== undefined &&
    verifyPassword !== ""
  ) {
    if (newPassword !== verifyPassword) {
      return "Passwords do not match";
    }
    return "";
  }
  return "";
}

function validatePasswords(failedOn: string[]): PasswordValidationErrors {
  const errors: PasswordValidationErrors = {
    password: "",
    oldPassword: "",
    verifyPassword: "",
  };

  // First sort out async password checks
  const passwordErrors: string[] = [];
  const oldPasswordErrors: string[] = [];
  if (failedOn.length > 0) {
    failedOn.forEach(failureType => {
      if (failureType === "LENGTH") {
        passwordErrors.push("Your new password is not long enough.");
      } else if (failureType === "COMPLEXITY") {
        passwordErrors.push(
          "Your new password not meet the password complexity requirements.",
        );
      } else if (failureType === "BAD_OLD_PASSWORD") {
        oldPasswordErrors.push("Your old password is not correct.");
      } else if (failureType === "REUSE") {
        passwordErrors.push("You may not reuse your old password.");
      } else {
        passwordErrors.push(
          "There is a problem with changing your password, please contact an administrator",
        );
      }
    });
  }
  if (passwordErrors.length > 0) {
    errors.password = passwordErrors.join("\n");
  }

  if (oldPasswordErrors.length > 0) {
    errors.oldPassword = oldPasswordErrors.join("\n");
  }

  return errors;
}

/**
 * A do-all async validation function for passwords.
 * @param email
 * @param newPassword
 * @param verifyPassword
 * @param url
 * @param oldPassword
 */
export async function validateAsync(
  email: string,
  newPassword: string,
  verifyPassword: string,
  url: string,
  oldPassword?: string,
) {
  let errors: PasswordValidationErrors = {
    oldPassword: "",
    password: "",
    verifyPassword: "",
  };
  if (newPassword !== undefined) {
    const result = await fetch(`${url}/noauth/isPasswordValid`, {
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
      },
      method: "post",
      mode: "cors",
      body: JSON.stringify({
        email,
        newPassword,
        oldPassword,
      }),
    });

    const { failedOn }: any = await result.json();
    if (failedOn) {
      errors = validatePasswords(failedOn);
    }
  }
  errors.verifyPassword = validateVerifyPassword(newPassword, verifyPassword);
  let errorMessage = "";
  if (errors.oldPassword !== "") {
    errorMessage += errors.oldPassword;
  }
  if (errors.password !== "") {
    errorMessage += errors.password;
  }
  if (errors.verifyPassword !== "") {
    errorMessage += errors.verifyPassword;
  }

  // Why return undefined? If we return anything else react-hook-form decides validation has failed.
  if (errorMessage === "") return undefined;
  else return errorMessage;
}
