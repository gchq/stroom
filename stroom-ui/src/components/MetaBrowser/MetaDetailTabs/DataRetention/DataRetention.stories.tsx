import * as React from "react";
import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import fullTestData from "testing/data";
import DataRetention from "./DataRetention";
import { MetaRow } from "components/MetaBrowser/types";

const dataRow: MetaRow = fullTestData.dataList.streamAttributeMaps[0];

const stories = storiesOf(
  "Sections/Meta Browser/Detail Tabs",
  module,
);

addThemedStories(stories, "Data Retention", () => <DataRetention dataRow={dataRow} />);
