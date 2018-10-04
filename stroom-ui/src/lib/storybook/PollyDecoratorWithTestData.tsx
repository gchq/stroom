import * as React from "react";
import { RenderFunction } from "@storybook/react";
import { setupTestServer, TestData } from "../../lib/storybook/PollyDecorator";

import { fromSetupSampleData } from "../../components/FolderExplorer/test";
import {
  testPipelines,
  elements,
  elementProperties
} from "../../components/PipelineEditor/test";
import { testDocRefsTypes } from "../../components/DocRefTypes/test";
import { testXslt } from "../../components/XsltEditor/test";
import { testDictionaries } from "../../components/DictionaryEditor/test";
import { generateGenericTracker } from "../../sections/Processing/tracker.testData";
import { dataList, dataSource } from "../../sections/DataViewer/test";

const testData: TestData = {
  documentTree: fromSetupSampleData,
  docRefTypes: testDocRefsTypes,
  elements,
  elementProperties,
  pipelines: testPipelines,
  xslt: testXslt,
  dictionaries: testDictionaries,
  dataList,
  dataSource,
  trackers: Array(10).map(i => generateGenericTracker(i))
};

const PollyComponent = setupTestServer(testData)(({ children }) => (
  <div className="fill-space">{children}</div>
));

export const PollyDecorator = (props: any) => (storyFn: RenderFunction) => (
  <PollyComponent {...props}>{storyFn()}</PollyComponent>
);
