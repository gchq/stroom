import * as React from "react";
import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import MetaTable, { useTable } from "./MetaTable";
import fullTestData from "testing/data";

const TestHarness: React.FunctionComponent = () => {
  const props = useTable(fullTestData.dataList);

  return <MetaTable {...props} />;
};

const stories = storiesOf("Sections/Meta Browser", module);

addThemedStories(stories, "Meta Table", () => <TestHarness />);
