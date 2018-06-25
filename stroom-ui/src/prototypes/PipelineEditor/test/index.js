import inherited from './pipeline.inherited.testData';
import simple from './pipeline.simple.testData';
import singleElement from './pipeline.singleElement.testData';
import longPipeline from './pipeline.longPipeline.testData';
import forkedPipeline from './pipeline.forkedPipeline.testData';
import childRestoredLink from './pipeline.childRestoredLink.testData';
import multiBranchChild from './pipeline.multiBranchChild.testData';
import multiBranchParent from './pipeline.multiBranchParent.testData';

import elementProperties from './elementProperties.testData';
import elements from './elements.testData';

module.exports = {
  testPipelines: {
    inherited,
    simple,
    forkedPipeline,
    childRestoredLink,
    multiBranchChild,
    multiBranchParent,
    singleElement,
    longPipeline,
  },
  elementProperties,
  elements,
};
