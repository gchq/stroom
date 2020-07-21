import * as React from "react";
import { storiesOf } from "@storybook/react";
import fullTestData from "testing/data";
import MetaRelations from "./MetaRelations";
import { MetaRow } from "../types";

const metaRow: MetaRow = fullTestData.dataList.streamAttributeMaps[0];

storiesOf("Sections/Meta Browser", module).add("Relations", () => (
  <MetaRelations metaRow={metaRow} />
));
