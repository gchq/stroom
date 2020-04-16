import * as React from "react";

import * as Cookies from "cookies-js";
import { storiesOf } from "@storybook/react";
import useAuthentication from "./useAuthentication";
import useForm from "lib/useForm";
import JsonDebug from "testing/JsonDebug";

const TEST_EMAIL = "me@you.com";
const TEST_PASSWORD = "secret";
const TEST_COOKIE = "StorybookCookie";

interface FormValues {
  email: string;
  password: string;
}

const TestHarness: React.FunctionComponent = () => {
  React.useEffect(() => {
    Cookies.set("authSessionId", TEST_COOKIE);
  }, []);

  const { login } = useAuthentication();
  const {
    useTextInput,
    value: { email, password },
  } = useForm<FormValues>({
    initialValues: {
      email: TEST_EMAIL,
      password: TEST_PASSWORD,
    },
  });
  const emailProps = useTextInput("email");
  const passwordProps = useTextInput("password");

  const [submitting, setSubmitting] = React.useState<boolean>(false);
  const [status, setStatus] = React.useState<string | undefined>(undefined);

  const onLogin = React.useCallback(() => login({ email, password }), [
    email,
    password,
    login,
    setSubmitting,
    setStatus,
  ]);

  return (
    <div>
      <form>
        <label>Email</label>
        <input {...emailProps} />
        <label>Password</label>
        <input {...passwordProps} />
      </form>
      <button onClick={onLogin}>Test Login</button>
      <JsonDebug value={{ email, password, submitting, status, TEST_COOKIE }} />
    </div>
  );
};

storiesOf("Auth/useAuthentication", module).add("login", () => <TestHarness />);
