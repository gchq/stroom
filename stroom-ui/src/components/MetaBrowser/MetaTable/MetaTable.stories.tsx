import * as React from "react";
import { storiesOf } from "@storybook/react";
import MetaTable, { useTable } from "./MetaTable";
import fullTestData from "testing/data";

const TestHarness: React.FunctionComponent = () => {
  const props = useTable(fullTestData.dataList);

  return <MetaTable {...props} />;
};

const stories = storiesOf("Sections/Meta Browser", module);

stories.add("Meta Table", () => <TestHarness />);
