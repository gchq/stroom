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

import Button from "components/Button";
import InlineSelect, {
  SelectOption,
} from "components/InlineSelect/InlineSelect";
import useListReducer from "lib/useListReducer";
import * as React from "react";
import { ChangeEvent, useEffect, useMemo, useCallback } from "react";

const getKey = (k: string) => k;

interface ValueAndChangeHandler {
  value: string;
  onChange: React.ChangeEventHandler<HTMLSelectElement>;
  onRemove: () => void;
}

interface Props {
  options?: SelectOption[];
  selected?: string[];
  onChange?: (selectOptions: string[]) => void;
}

const InlineMultiSelect: React.FunctionComponent<Props> = ({
  options,
  selected,
  onChange,
  ...rest
}) => {
  // Use the standard list reducer to manage the items
  const {
    items,
    addItem,
    updateItemAtIndex,
    removeItemAtIndex,
  } = useListReducer<string>(getKey, selected);

  const addNewItem = useCallback(() => addItem(undefined), [addItem]);
  useEffect(() => onChange(items), [onChange, items]);

  const valuesAndChangeHandlers: ValueAndChangeHandler[] = useMemo(
    () =>
      selected.map((value, valueIndex) => ({
        onChange: ({
          target: { value: newValue },
        }: ChangeEvent<HTMLSelectElement>) =>
          updateItemAtIndex(valueIndex, newValue),
        onRemove: () => removeItemAtIndex(valueIndex),
        value,
      })),
    [selected, updateItemAtIndex, removeItemAtIndex],
  );

  // We only want to allow something to be selected once, so we need to
  // work out what options are remaining...
  const remainingOptions = options.filter(
    option => items.indexOf(option.value) < 0,
  );
  return (
    <span>
      [
      {valuesAndChangeHandlers.map(({ value, onChange, onRemove }, index) => {
        //... but we must have this option present in the list, otherwise
        // it can't be selected
        const thisSelectsOptions = Object.assign([], remainingOptions);
        const selectedOption = options.find(option => option.value === value);
        if (!!selectedOption) {
          thisSelectsOptions.push(selectedOption);
        }
        return (
          <React.Fragment key={index}>
            <InlineSelect
              options={thisSelectsOptions}
              selected={value}
              onChange={onChange}
              // If we don't have a value then we're showing a newly
              // added select, and we want it focused so it's editable
              // and doesn't have to be clicked. I.e. this saves a click.
              autoFocus={value === undefined || value === ""}
              {...rest}
            />
            <Button
              size="small"
              type="button"
              appearance="icon"
              action="secondary"
              text="Remove"
              icon="times"
              title="Remove"
              onClick={onRemove}
            />
            {/* we only want to display this if we're not at the end of the list */}
            {index !== options.length - 1 ? (
              <span>,{"\u00A0"}</span>
            ) : (
              undefined
            )}
          </React.Fragment>
        );
      })}
      {/* We only want to display this if there are options left to select */}
      {remainingOptions.length > 0 ? (
        <Button
          size="small"
          type="button"
          appearance="icon"
          action="primary"
          text="Add"
          icon="plus"
          title="Add"
          onClick={addNewItem}
        />
      ) : (
        undefined
      )}
      ]
    </span>
  );
};

export default InlineMultiSelect;
