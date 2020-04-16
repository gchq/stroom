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
import InlineInput from "components/InlineInput/InlineInput";
import { ControlledInput } from "lib/useForm/types";
import useListReducer from "lib/useListReducer";
import * as React from "react";
import { ChangeEvent, useCallback, useEffect, useMemo } from "react";

const getKey = (k: string) => k;

interface ValueAndChangeHandler {
  value: string;
  onChange: React.ChangeEventHandler<HTMLInputElement>;
  onRemove: () => void;
}

const InlineMultiInput: React.FunctionComponent<ControlledInput<string[]>> = ({
  onChange,
  value: values,
  ...rest
}) => {
  // Use the standard list reducer to manage the items
  const {
    items,
    addItem,
    updateItemAtIndex,
    removeItemAtIndex,
  } = useListReducer<string>(getKey, values);

  const addNewItem = useCallback(() => addItem(""), [addItem]);

  // When the items change in the list, we call the onChange handler
  useEffect(() => onChange(items), [onChange, items]);

  // Create memoized versions of the onChange and onRemove functions for each item
  const valuesOnChangeHandlers: ValueAndChangeHandler[] = useMemo(
    () =>
      values.map((value, valueIndex) => ({
        onChange: ({
          target: { value: newValue },
        }: ChangeEvent<HTMLInputElement>) =>
          updateItemAtIndex(valueIndex, newValue),
        onRemove: () => removeItemAtIndex(valueIndex),
        value,
      })),
    [values, updateItemAtIndex, removeItemAtIndex],
  );

  return (
    <span>
      [
      {valuesOnChangeHandlers.map(({ value, onChange, onRemove }, index) => (
        <React.Fragment key={index}>
          <InlineInput value={value} onChange={onChange} {...rest} />
          <Button
            type="button"
            size="small"
            appearance="icon"
            action="secondary"
            text="Remove"
            icon="times"
            title="Remove"
            onClick={onRemove}
          />
          {index !== values.length - 1 ? <span>,{"\u00A0"}</span> : undefined}
        </React.Fragment>
      ))}
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
      ]
    </span>
  );
};

export default InlineMultiInput;
