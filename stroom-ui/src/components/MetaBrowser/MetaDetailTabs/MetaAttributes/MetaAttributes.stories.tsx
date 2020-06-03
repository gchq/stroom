import * as React from "react";
import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import fullTestData from "testing/data";
import MetaAttributes from "./MetaAttributes";
import { MetaRow } from "components/MetaBrowser/types";

const dataRow: MetaRow = fullTestData.dataList.streamAttributeMaps[0];

const stories = storiesOf(
  "Sections/Meta Browser/Detail Tabs",
  module,
);

addThemedStories(stories, "Meta Attributes", () => <MetaAttributes dataRow={dataRow} />);
