import * as React from "react";
import { storiesOf } from "@storybook/react";

import FolderExplorer from "./FolderExplorer";
import StroomDecorator from "../../lib/storybook/StroomDecorator";

import "../../styles/main.css";

const { fromSetupSampleData } = require("./test");

const testFolder1 = fromSetupSampleData.children[0];

storiesOf("Explorer/Folder", module)
  .addDecorator(StroomDecorator)
  .add("simple", () => <FolderExplorer folderUuid={testFolder1.uuid} />);
