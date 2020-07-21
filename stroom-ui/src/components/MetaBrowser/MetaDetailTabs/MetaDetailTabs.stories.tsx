import * as React from "react";
import { storiesOf } from "@storybook/react";
import fullTestData from "testing/data";
import MetaDetailTabs from "./MetaDetailTabs";
import { MetaRow } from "../types";

const data: MetaRow = fullTestData.dataList.streamAttributeMaps[0];

storiesOf("Sections/Meta Browser", module).add("Detail Tabs", () => (
  <MetaDetailTabs metaRow={data} />
));
