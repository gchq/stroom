import * as React from "react";

import { storiesOf } from "@storybook/react";
import JsonDebug from "testing/JsonDebug";

import { testDataSource as dataSource } from "../test";
import DataSourceFieldPicker from "./DataSourceFieldPicker";

const TestHarness: React.FunctionComponent = () => {
  const [value, onChange] = React.useState<string | undefined>(undefined);

  return (
    <div>
      <DataSourceFieldPicker {...{ dataSource, value, onChange }} />
      <JsonDebug value={{ value, dataSource }} />
    </div>
  );
};

storiesOf("Expression", module).add("Data Source Field Picker", () => (
  <TestHarness />
));
