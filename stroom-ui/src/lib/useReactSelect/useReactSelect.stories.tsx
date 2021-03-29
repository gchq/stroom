import * as React from "react";
import { storiesOf } from "@storybook/react";
import Select from "react-select";
import { useReactSelect } from "./useReactSelect";
import JsonDebug from "testing/JsonDebug";

const OPTIONS: string[] = ["Dog", "Cat", "Rat", "Snake", "Badger", "Lion"];

const TestHarness: React.FunctionComponent = () => {
  const [value, onChange] = React.useState<string>("");

  const { _onChange, _value, _options } = useReactSelect({
    value,
    onChange,
    options: OPTIONS,
  });

  return (
    <div>
      <h1>React Select Wrapper Hook</h1>
      <p>
        This hook can be used when the list of options is simply a set of
        strings
      </p>
      <p>
        React Select prefers to have a set of objects to work with so this hook
        translates the options appropriately.
      </p>
      <form>
        <label>Favourite Animal</label>
        <Select onChange={_onChange} value={_value} options={_options} />
      </form>
      <JsonDebug value={value} />
    </div>
  );
};

storiesOf("lib/useReactSelect", module).add("test", () => <TestHarness />);
