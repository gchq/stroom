/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import * as React from "react";
import { storiesOf } from "@storybook/react";

import useSelectableItemListing from "./useSelectableItemListing";
import { SelectionBehaviour } from "./enums";
import JsonDebug from "testing/JsonDebug";
import Button from "components/Button";
import useTestAnimals, { Animal } from "./useTestAnimals";

const TestList = () => {
  const [lastAction, setLastAction] = React.useState<string>("no action");
  const [externalSelectedItem, setExternalSelectedItem] = React.useState<
    Animal | undefined
  >(undefined);
  const { animals, preFocusWrap, reset } = useTestAnimals();

  const {
    onKeyDown,
    selectedIndexes,
    selectedItem,
    focusIndex,
    toggleSelection,
    ...restOfSelectable
  } = useSelectableItemListing({
    getKey: React.useCallback((a) => a.name, []),
    openItem: React.useCallback((a) => setLastAction(`Opened Item ${a.name}`), [
      setLastAction,
    ]),
    items: animals,
    selectionBehaviour: SelectionBehaviour.MULTIPLE,
    preFocusWrap,
  });

  // Demonstrates how to 'watch' for selection changes
  React.useEffect(() => setExternalSelectedItem(selectedItem), [
    selectedItem,
    setExternalSelectedItem,
  ]);

  return (
    <div tabIndex={0} onKeyDown={onKeyDown}>
      <h3>Test Selectable Item Listing</h3>
      <p>
        The lightblue items are selected items, the black bordered item is the
        focused one. If you focus scroll off the end of the list, it should demo
        the pre-focus wrap hook which allows the calling component to request
        more data instead.
      </p>
      <Button onClick={reset}>Reset</Button>
      <ul>
        {animals.map((animal, i) => (
          <li
            key={i}
            onClick={() => toggleSelection(animal.name)}
            style={{
              borderStyle: focusIndex === i ? "solid" : "none",
              backgroundColor: selectedIndexes.includes(i)
                ? "lightblue"
                : "white",
            }}
          >
            {animal.species} - {animal.name}
          </li>
        ))}
      </ul>
      <JsonDebug
        value={{
          lastAction,
          externalSelectedItem,
          selectedIndexes,
          selectedItem,
          focusIndex,
          ...restOfSelectable,
        }}
      />
    </div>
  );
};

storiesOf("lib/useSelectableItemListing", module).add("A List", TestList);
