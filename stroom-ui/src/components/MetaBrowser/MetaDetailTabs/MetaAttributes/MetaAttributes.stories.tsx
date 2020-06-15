import * as React from "react";
import { storiesOf } from "@storybook/react";
import fullTestData from "testing/data";
import MetaAttributes from "./MetaAttributes";
import { MetaRow } from "components/MetaBrowser/types";

const dataRow: MetaRow = fullTestData.dataList.streamAttributeMaps[0];

storiesOf("Sections/Meta Browser/Detail Tabs", module).add(
  "Meta Attributes",
  () => <MetaAttributes dataRow={dataRow} />,
);
