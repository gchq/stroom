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

const changePasswordValidationSchema = Yup.object().shape({
  oldPassword: Yup.string().required("Please enter your old password"),
  password: Yup.string().required("Please enter your new password"),
  verifyPassword: Yup.string().required("Please re-enter your new password"),
});

const resetPasswordValidationSchema = Yup.object().shape({
  password: Yup.string().required("Required"),
  verifyPassword: Yup.string().required("Required"),
});

export { changePasswordValidationSchema, resetPasswordValidationSchema };
