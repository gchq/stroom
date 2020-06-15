import { storiesOf } from "@storybook/react";
import useListReducer from "lib/useListReducer";
import { loremIpsum } from "lorem-ipsum";
import * as React from "react";
import JsonDebug from "testing/JsonDebug";
import useCustomFocus from "./useCustomFocus";

const generateItem = () => loremIpsum({ count: 3, units: "words" });

const TEST_ITEMS: string[] = Array(5).fill(null).map(generateItem);

const focusStyle: React.CSSProperties = {
  border: "solid thin black",
};

interface Props {
  initialItems: string[];
}

interface ItemWithClick<T> {
  item: T;
  onClick: () => void;
}

const TestHarness: React.FunctionComponent<Props> = ({ initialItems }) => {
  const { items, removeItemAtIndex, addItem } = useListReducer(
    (d) => d,
    initialItems,
  );

  const preFocusWrap = React.useCallback(() => {
    if (items.length < 9) {
      addItem(generateItem());
      return false;
    } else {
      return true;
    }
  }, [items, addItem]);

  const {
    setByIndex,
    down,
    up,
    clear,
    focusIndex,
    highlightedItem,
  } = useCustomFocus({
    items,
    preFocusWrap,
  });

  const removeFirstItem = React.useCallback(() => removeItemAtIndex(0), [
    removeItemAtIndex,
  ]);

  const itemsWithClick: ItemWithClick<string>[] = React.useMemo(
    () =>
      items.map((item, i) => ({
        item,
        onClick: () => setByIndex(i),
      })),
    [items, setByIndex],
  );

  return (
    <div>
      <h1>Custom Focus Test</h1>
      {itemsWithClick.map(({ item, onClick }, i) => (
        <div
          key={i}
          onClick={onClick}
          style={i === focusIndex ? focusStyle : {}}
        >
          {item}
        </div>
      ))}
      <button onClick={up}>Up</button>
      <button onClick={down}>Down</button>
      <button onClick={clear}>Clear</button>
      <button onClick={removeFirstItem}>Remove First</button>
      <JsonDebug value={{ focusIndex, highlightedItem }} />
    </div>
  );
};

storiesOf("lib/useCustomFocus", module).add("test", () => (
  <TestHarness initialItems={TEST_ITEMS} />
));
