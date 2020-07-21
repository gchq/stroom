import * as React from "react";
import { storiesOf } from "@storybook/react";
import JsonDebug from "testing/JsonDebug";
import NewUserForm, { useThisForm } from "./NewUserForm";

const TestHarness: React.FunctionComponent = () => {
  const { value, componentProps } = useThisForm();

  return (
    <div>
      <NewUserForm {...componentProps} />
      <JsonDebug value={value} />
    </div>
  );
};

storiesOf("Sections/Authorisation Manager/New User", module).add("Form", () => (
  <TestHarness />
));
