import { storiesOf } from "@storybook/react";
import * as React from "react";
import { SignInForm, SignInPage } from "./SignIn";
import { Formik } from "formik";
import * as Yup from "yup";
import { usePrompt } from "components/Prompt/PromptDisplayBoundary";
import { useState } from "react";
import { AuthState } from "./api/types";
import BackgroundLogo from "../Layout/BackgroundLogo";
import FormContainer from "../Layout/FormContainer";
import Background from "../Layout/Background";

const TestHarness: React.FunctionComponent<{
  allowPasswordResets?: boolean;
}> = ({ allowPasswordResets = false }) => {
  const { showInfo } = usePrompt();
  const [authState, setAuthState] = useState<AuthState>({
    allowPasswordResets,
  });

  return (
    <Background>
      <BackgroundLogo>
        <FormContainer>
          <SignInPage authState={authState} setAuthState={setAuthState}>
            <Formik
              initialValues={{ userId: "", password: "" }}
              validationSchema={Yup.object().shape({
                userId: Yup.string()
                  .email("User name not valid")
                  .required("User name is required"),
                password: Yup.string().required("Password is required"),
              })}
              onSubmit={(values, actions) => {
                setTimeout(() => {
                  showInfo({
                    title: "Submitted",
                    message: JSON.stringify(values, null, 2),
                  });
                  actions.setSubmitting(false);
                }, 1000);
              }}
            >
              {(props) => <SignInForm {...props} />}
            </Formik>
          </SignInPage>
        </FormContainer>
      </BackgroundLogo>
    </Background>
  );
};

const stories = storiesOf("Authentication", module);
stories.add("Sign In - allow password resets", () => (
  <TestHarness allowPasswordResets={true} />
));
stories.add("Sign In - disallow password resets", () => (
  <TestHarness allowPasswordResets={false} />
));
