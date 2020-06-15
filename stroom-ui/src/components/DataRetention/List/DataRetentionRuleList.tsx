/*
 * Copyright 2019 Crown Copyright
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

import { ControlledInput } from "lib/useForm/types";
import useListReducer from "lib/useListReducer";
import * as React from "react";
import { useCallback, useEffect, useMemo } from "react";
import { DragDropContext, Droppable, DropResult } from "react-beautiful-dnd";
import DataRetentionRuleEditor from "../Editor/DataRetentionRuleEditor";
import { DataRetentionRule } from "../types/DataRetentionRule";

const getKey = (k: DataRetentionRule) => k.ruleNumber.toString();

interface ValueAndChangeHandler {
  value: DataRetentionRule;
  onChange: (rule: DataRetentionRule) => void;
  onRemove: () => void;
}

const DataRetentionRuleList: React.FunctionComponent<ControlledInput<
  DataRetentionRule[]
>> = ({ value: values, onChange }) => {
  const sortedRules = useMemo(
    () => values.sort((l, r) => (l.ruleNumber >= r.ruleNumber ? 1 : -1)),
    [values],
  );

  const {
    items,
    // addItem, //TODO: allow a user to add rules
    updateItemAtIndex,
    removeItemAtIndex,
  } = useListReducer<DataRetentionRule>(getKey, sortedRules);

  useEffect(() => onChange(items), [onChange, items]);

  const valuesAndChangeHandlers: ValueAndChangeHandler[] = useMemo(
    () =>
      sortedRules.map((value, valueIndex) => ({
        onChange: (newRule: DataRetentionRule) => {
          updateItemAtIndex(valueIndex, newRule);
        },
        onRemove: () => removeItemAtIndex(valueIndex),
        value,
      })),
    [sortedRules, updateItemAtIndex, removeItemAtIndex],
  );

  const handleOnDragEnd = useCallback(
    (result: DropResult) => {
      if (!!result.destination) {
        const {
          source: { index: sourceIndex },
          destination: { index: destinationIndex },
        } = result;

        items.splice(destinationIndex, 0, items.splice(sourceIndex, 1)[0]);
        const itemsWithUpdatedRuleNumbers = items.map((item, index) => {
          item.ruleNumber = index + 1;
          return item;
        });
        onChange(itemsWithUpdatedRuleNumbers);
      }
    },
    [onChange, items],
  );
  return (
    <DragDropContext onDragEnd={handleOnDragEnd}>
      <Droppable droppableId="dataRetentionRuleListDroppable">
        {(provided) => (
          <div ref={provided.innerRef} {...provided.droppableProps}>
            {valuesAndChangeHandlers
              .sort((l, r) =>
                l.value.ruleNumber >= r.value.ruleNumber ? 1 : -1,
              )
              .map(
                ({ value: rule, onChange: onRuleChange, onRemove }, index) => {
                  return (
                    <React.Fragment key={index}>
                      <div key={index} className="DataRetentionRuleList__rule">
                        <DataRetentionRuleEditor
                          value={rule}
                          onChange={onRuleChange}
                          onDelete={onRemove}
                          index={index}
                        />
                      </div>
                    </React.Fragment>
                  );
                },
              )}
            {provided.placeholder}
          </div>
        )}
      </Droppable>
    </DragDropContext>
  );
};

export default DataRetentionRuleList;
