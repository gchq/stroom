import { PollyDecorator } from 'lib/storybook/PollyDecorator';

import { fromSetupSampleData } from 'components/FolderExplorer/test';
import { testPipelines, elements, elementProperties } from 'components/PipelineEditor/test';
import { testDocRefsTypes } from 'components/DocRefTypes/test';
import { testXslt } from 'components/XsltEditor/test';
import { testDictionaries } from 'components/DictionaryEditor/test';
import { generateGenericTracker } from 'sections/Processing/tracker.testData';
import { dataList, dataSource } from 'sections/DataViewer/test';

export const PollyDecoratorWithTestData = PollyDecorator({
  documentTree: fromSetupSampleData,
  docRefTypes: testDocRefsTypes,
  elements,
  elementProperties,
  pipelines: testPipelines,
  xslt: testXslt,
  dictionaries: testDictionaries,
  dataList,
  dataSource,
  trackers: [...Array(10).keys()].map(i => generateGenericTracker(i)),
});
