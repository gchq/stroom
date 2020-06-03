import * as React from "react";
import { storiesOf } from "@storybook/react";
import { addThemedStories } from "testing/storybook/themedStoryGenerator";
import fullTestData from "testing/data";
import MetaRelations from "./MetaRelations";
import { MetaRow } from "../types";

const metaRow: MetaRow = fullTestData.dataList.streamAttributeMaps[0];

const stories = storiesOf("Sections/Meta Browser", module);

addThemedStories(stories, "Relations", () => <MetaRelations metaRow={metaRow} />);
