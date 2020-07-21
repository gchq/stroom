import * as React from "react";
import { storiesOf } from "@storybook/react";

import CheckboxSeries from "./CheckboxSeries";
import JsonDebug from "testing/JsonDebug";

const AVAILABLE_VALUES: string[] = [
  "cereal",
  "toast",
  "sausages",
  "eggs",
  "hash browns",
];

const TestHarness: React.FunctionComponent = () => {
  const [breakfast, setBreakfast] = React.useState<string[]>([]);

  const addBreakfast = React.useCallback(
    (t) => setBreakfast(breakfast.concat([t])),
    [setBreakfast, breakfast],
  );
  const removeBreakfast = React.useCallback(
    (t) => setBreakfast(breakfast.filter((b) => b !== t)),
    [setBreakfast, breakfast],
  );

  return (
    <div>
      <h1>Breakfast Selection</h1>
      <CheckboxSeries
        allValues={AVAILABLE_VALUES}
        includedValues={breakfast}
        addValue={addBreakfast}
        removeValue={removeBreakfast}
      />

      <JsonDebug value={breakfast} />
    </div>
  );
};

storiesOf("General Purpose", module).add("Checkbox Series", () => (
  <TestHarness />
));
