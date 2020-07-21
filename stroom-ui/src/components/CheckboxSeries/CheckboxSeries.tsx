import * as React from "react";

/**
 * This component can be used whenever you have a list of named values, and a list of values
 * which have been selected. This component will render a checkbox for each value
 * and tick all the boxes that are inside the 'includedValues' list.
 * It requires even handlers which handle the adding and removal of values as they are ticked/unticked.
 */
interface Props {
  allValues: string[];
  includedValues: string[];
  addValue: (value: string) => void;
  removeValue: (value: string) => void;
}

export const CheckboxSeries: React.FunctionComponent<Props> = ({
  allValues = [],
  includedValues = [],
  addValue,
  removeValue,
}) => {
  return (
    <div>
      {allValues
        .map((value) => ({
          value,
          isSelected: includedValues.includes(value),
        }))
        .map(({ value, isSelected }) => (
          <div key={value}>
            <label>{value}</label>
            <input
              type="checkbox"
              checked={isSelected}
              onChange={() => {
                if (isSelected) {
                  removeValue(value);
                } else {
                  addValue(value);
                }
              }}
            />
          </div>
        ))}
    </div>
  );
};

export default CheckboxSeries;
