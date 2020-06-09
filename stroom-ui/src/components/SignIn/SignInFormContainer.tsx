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

// import * as React from "react";
import useAuthentication from "components/authentication/useAuthentication";
// import useConfig from "startup/config/useConfig";
import SignInForm from "./SignInForm";
import { Formik } from "formik";
import * as Yup from "yup";
import * as React from "react";

// const SignInFormContainer = withFormik<MyFormProps, FormValues>({
//   mapPropsToValues: props => ({
//     email: props.initialEmail || "",
//     password: props.initialPassword || "",
//   }),
//
//   validationSchema: Yup.object().shape({
//     email: Yup.string()
//       .email("Email not valid")
//       .required("Email is required"),
//     password: Yup.string().required("Password is required"),
//   }),
//
//   handleSubmit(
//     { email, password }: FormValues,
//     { props, setSubmitting, setErrors },
//   ) {
//     login({ email, password });
//     // console.log(email, password);
//   },
// })(SignInForm);

export const SignInFormContainer: React.FunctionComponent = () => {
  const { login, isSubmitting } = useAuthentication();
  return (
    <Formik
      initialValues={{ email: "", password: "" }}
      validationSchema={Yup.object().shape({
        email: Yup.string()
          .email("Email not valid")
          .required("Email is required"),
        password: Yup.string().required("Password is required"),
      })}
      onSubmit={(values, actions) => {
        login(values);

        // setTimeout(() => {
        //   alert(JSON.stringify(values, null, 2));
        //   actions.setSubmitting(false);
        // }, 1000);
      }}
    >
      {props => (
        <SignInForm allowPasswordResets={true} {...props} />
        // <form onSubmit={props.handleSubmit}>
        //   <input
        //     type="text"
        //     onChange={props.handleChange}
        //     onBlur={props.handleBlur}
        //     value={props.values.name}
        //     name="name"
        //   />
        //   {props.errors.name && <div id="feedback">{props.errors.name}</div>}
        //   <button type="submit">Submit</button>
        // </form>
      )}
    </Formik>
  );
};

// const SignInFormContainer = () => {
//   const { allowPasswordResets } = useConfig();
//   const { login, isSubmitting } = useAuthentication();
//   return (
//     <SignInForm
//       allowPasswordResets={allowPasswordResets}
//       isSubmitting={isSubmitting}
//       onSubmit={login}
//     />
//   );
// };

export default SignInFormContainer;
