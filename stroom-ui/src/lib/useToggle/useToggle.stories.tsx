import * as React from "react";
import { storiesOf } from "@storybook/react";
import JsonDebug from "testing/JsonDebug";
import { useToggle } from "./useToggle";
import Button from "components/Button";

const TestHarness: React.FunctionComponent = () => {
  const { value, toggle } = useToggle();

  return (
    <div>
      <Button onClick={toggle}>Toggle</Button>
      <JsonDebug value={{ value }} />
    </div>
  );
};

storiesOf("lib/useToggle", module).add("simple", () => <TestHarness />);
