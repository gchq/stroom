import * as React from "react";

import { storiesOf } from "@storybook/react";
import useLocalStorage, { useStoreObjectFactory } from "./useLocalStorage";
import JsonDebug from "testing/JsonDebug";

interface TestStore1 {
  name: string;
}

const TestHarnessSetValue: React.FunctionComponent = () => {
  const storageKey = "testWithSetValue";

  const { value, setValue, resetValue } = useLocalStorage<TestStore1>(
    storageKey,
    {
      name: "someName",
    },
    useStoreObjectFactory(),
  );

  const onName1Change: React.ChangeEventHandler<
    HTMLInputElement
  > = React.useCallback(
    ({ target: { value } }) => {
      setValue({ name: value });
    },
    [setValue],
  );

  const resetStorage = React.useCallback(() => {
    resetValue();
  }, [resetValue]);

  return (
    <div>
      <p>Demonstrates simple use of setValue</p>
      <form>
        <div>
          <label>Value in Storage</label>
          <input value={value.name} onChange={onName1Change} />
        </div>
      </form>

      <div>
        <button onClick={resetStorage}>Reset All Storage</button>
      </div>
      <JsonDebug value={{ storageKey, value }} />
    </div>
  );
};

interface TestStore2 {
  names: string[];
}

const TestHarnessReducer: React.FunctionComponent = () => {
  const storageKey = "testWithReducer";

  const [newValue, setNewValue] = React.useState<string>("kochanski");
  const onNewValueChange: React.ChangeEventHandler<
    HTMLInputElement
  > = React.useCallback(({ target: { value } }) => setNewValue(value), [
    setNewValue,
  ]);

  const { value, reduceValue, resetValue } = useLocalStorage<TestStore2>(
    storageKey,
    {
      names: ["lister", "rimmer", "cat", "kryten"],
    },
    useStoreObjectFactory(),
  );

  const onAddValue = React.useCallback(
    e => {
      reduceValue(existing => ({ names: [newValue, ...existing.names] }));
      e.preventDefault();
    },
    [newValue, reduceValue],
  );

  const onRemoveValue = React.useCallback(
    e => {
      reduceValue(existing => ({
        names: existing.names.filter(e => e !== newValue),
      }));
      e.preventDefault();
    },
    [newValue, reduceValue],
  );

  const resetStorage = React.useCallback(() => {
    resetValue();
  }, [resetValue]);

  return (
    <div>
      <p>
        Demonstrates the use of a reducer, where any new value is calculated
        with reference to the existing one.
      </p>
      <p>
        If a reducer is not used, and instead a function is memoized that gets
        recreated when the value changes, you end up with a recursive render. So
        for any local storage values that need to use the existing value to
        calculate the new value, use a reducer.
      </p>
      <form>
        <div>
          <label>Value for Storage</label>
          <input value={newValue} onChange={onNewValueChange} />
          <button onClick={onAddValue}>Add Value</button>
          <button onClick={onRemoveValue}>Remove Value</button>
        </div>
      </form>

      <div>
        <button onClick={resetStorage}>Reset All Storage</button>
      </div>
      <JsonDebug value={{ storageKey, value }} />
    </div>
  );
};

storiesOf("lib/useLocalStorage", module)
  .add("setValue", () => <TestHarnessSetValue />)
  .add("reducer", () => <TestHarnessReducer />);
