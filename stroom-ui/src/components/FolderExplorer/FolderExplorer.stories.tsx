import * as React from "react";
import { storiesOf } from "@storybook/react";

import FolderExplorer from "./FolderExplorer";
import StroomDecorator from "../../lib/storybook/StroomDecorator";
import fullTestData from "../../lib/storybook/fullTestData";

import "../../styles/main.css";

const testFolder1 = fullTestData.documentTree.children![0];

storiesOf("Explorer/Folder", module)
  .addDecorator(StroomDecorator)
  .add("simple", () => <FolderExplorer folderUuid={testFolder1.uuid} />);
