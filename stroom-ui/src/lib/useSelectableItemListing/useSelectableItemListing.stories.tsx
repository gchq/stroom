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
import { useState } from "react";
import { storiesOf } from "@storybook/react";

import StroomDecorator from "../storybook/StroomDecorator";
import useSelectableItemListing, {
  SelectionBehaviour
} from "./useSelectableItemListing";

type Animal = {
  species: string;
  name: string;
};

let animals: Array<Animal> = [
  {
    species: "Dog",
    name: "Rover"
  },
  {
    species: "Cat",
    name: "Tiddles"
  },
  {
    species: "Mouse",
    name: "Pixie"
  },
  {
    species: "Tyrannosaurus Rex",
    name: "Fluffy"
  }
];

storiesOf("General Purpose/useSelectableItemListing", module)
  .addDecorator(StroomDecorator)
  .add("Test Component", () => {
    const [lastAction, setLastAction] = useState<string>("no action");

    const {
      onKeyDownWithShortcuts,
      selectedItemIndexes,
      focusIndex,
      selectionToggled
    } = useSelectableItemListing<Animal>({
      getKey: a => a.name,
      openItem: a => setLastAction(`Opened Item ${a.name}`),
      items: animals,
      selectionBehaviour: SelectionBehaviour.MULTIPLE
    });

    return (
      <div tabIndex={0} onKeyDown={onKeyDownWithShortcuts}>
        <h3>Test Selectable Item Listing</h3>
        <p>Last Action: {lastAction}</p>
        <ul>
          {animals.map((animal, i) => (
            <li
              key={i}
              onClick={() => selectionToggled(animal.name)}
              style={{
                borderStyle: focusIndex === i ? "solid" : "none",
                backgroundColor: selectedItemIndexes.has(i)
                  ? "lightblue"
                  : "white"
              }}
            >
              {animal.species} - {animal.name}
            </li>
          ))}
        </ul>
      </div>
    );
  });
