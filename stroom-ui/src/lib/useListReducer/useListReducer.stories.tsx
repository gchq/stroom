import { storiesOf } from "@storybook/react";
import * as React from "react";
import useListReducer from "./useListReducer";
import { loremIpsum } from "lorem-ipsum";

const generateItem = () => loremIpsum({ count: 3, units: "words" });

const TEST_ITEMS: string[] = Array(5).fill(null).map(generateItem);

interface Props {
  initialItems: string[];
}

const TestHarness: React.FunctionComponent<Props> = ({ initialItems }) => {
  const { items, addItem, removeItem } = useListReducer((c) => c, initialItems);

  const [newName, setNewName] = React.useState<string>(generateItem());

  const onNewNameChange: React.ChangeEventHandler<HTMLInputElement> = React.useCallback(
    ({ target: { value } }) => setNewName(value),
    [setNewName],
  );

  const onAddNewItem = React.useCallback(
    (e) => {
      addItem(newName);
      e.preventDefault();
    },
    [addItem, newName],
  );

  return (
    <div>
      <form>
        <label>Name</label>
        <input value={newName} onChange={onNewNameChange} />
        <button onClick={onAddNewItem}>Add</button>
      </form>
      {items.map((c) => (
        <div key={c}>
          {c}
          <button onClick={() => removeItem(c)}>Remove</button>
        </div>
      ))}
    </div>
  );
};

storiesOf("lib/useListReducer", module).add("test", () => (
  <TestHarness initialItems={TEST_ITEMS} />
));
