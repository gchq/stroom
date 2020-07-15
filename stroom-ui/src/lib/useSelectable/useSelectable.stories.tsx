import * as React from "react";
import { loremIpsum } from "lorem-ipsum";
import { storiesOf } from "@storybook/react";
import { useSelectable } from "./useSelectable";
import JsonDebug from "testing/JsonDebug";

const TEST_ITEMS: string[] = Array(10)
  .fill(null)
  .map(() => loremIpsum({ units: "words", count: 3 }));

const selectedStyle: React.CSSProperties = {
  border: "solid thin black",
};

interface Props {
  items: string[];
}

interface ItemWithClick<T> {
  item: T;
  onClick: () => void;
}

const TestHarness: React.FunctionComponent<Props> = ({ items }) => {
  const selectable = useSelectable({
    items,
    getKey: React.useCallback((d) => d, []),
  });
  const { selectedItems, toggleSelection, clearSelection } = selectable;

  const itemsWithOnClick: ItemWithClick<string>[] = React.useMemo(
    () =>
      items.map((item) => ({
        item,
        onClick: () => toggleSelection(item),
      })),
    [items, toggleSelection],
  );

  return (
    <div>
      <h1>Selectable Items</h1>
      <p>Click on items to toggle their selection</p>
      <ul>
        {itemsWithOnClick.map(({ item, onClick }, i) => (
          <li
            key={i}
            style={selectedItems.includes(item) ? selectedStyle : {}}
            onClick={onClick}
          >
            {item}
          </li>
        ))}
      </ul>
      <button onClick={clearSelection}>Clear</button>
      <JsonDebug value={selectable} />
    </div>
  );
};

storiesOf("lib/useSelectable", module).add("test", () => (
  <TestHarness items={TEST_ITEMS} />
));
