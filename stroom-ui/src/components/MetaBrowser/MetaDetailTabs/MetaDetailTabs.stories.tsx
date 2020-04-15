import * as React from "react";
import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import fullTestData from "testing/data";
import MetaDetailTabs from "./MetaDetailTabs";
import { MetaRow } from "../types";

const data: MetaRow = fullTestData.dataList.streamAttributeMaps[0];

const stories = storiesOf("Sections/Meta Browser/Detail Tabs", module);

addThemedStories(stories, () => <MetaDetailTabs metaRow={data} />);
