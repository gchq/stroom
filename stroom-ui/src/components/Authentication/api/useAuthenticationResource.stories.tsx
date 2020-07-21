import * as React from "react";

import * as Cookies from "cookies-js";
import { storiesOf } from "@storybook/react";
import { useAuthenticationResource } from "./useAuthenticationResource";
import useForm from "lib/useForm";
import JsonDebug from "testing/JsonDebug";

const TEST_EMAIL = "me@you.com";
const TEST_PASSWORD = "secret";
const TEST_COOKIE = "StorybookCookie";

interface FormValues {
  userId: string;
  password: string;
}

const TestHarness: React.FunctionComponent = () => {
  React.useEffect(() => {
    Cookies.set("authSessionId", TEST_COOKIE);
  }, []);

  const { login } = useAuthenticationResource();
  const {
    useTextInput,
    value: { userId, password },
  } = useForm<FormValues>({
    initialValues: {
      userId: TEST_EMAIL,
      password: TEST_PASSWORD,
    },
  });
  const userIdProps = useTextInput("userId");
  const passwordProps = useTextInput("password");

  // const [submitting, setSubmitting] = React.useState<boolean>(false);
  // const [status, setStatus] = React.useState<string | undefined>(undefined);

  const onLogin = React.useCallback(() => login({ userId, password }), [
    userId,
    password,
    login,
  ]);

  return (
    <div>
      <form>
        <label>User Id</label>
        <input {...userIdProps} />
        <label>Password</label>
        <input {...passwordProps} />
      </form>
      <button onClick={onLogin}>Test Login</button>
      <JsonDebug value={{ userId, password, TEST_COOKIE }} />
    </div>
  );
};

storiesOf("Authentication/api", module).add("useAuthenticationResource", () => (
  <TestHarness />
));
