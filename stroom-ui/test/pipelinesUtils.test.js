/*
 * Copyright 2018 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import expect from 'expect.js';

import { getPipelineAsTree } from '../src/prototypes/PipelineEditor/pipelineUtils';

import { testPipeline } from '../src/prototypes/PipelineEditor/storybook/testPipelines';

describe('Pipeline Utils', () => {
  describe('#getPipelineAsTree', () => {
    it('should convert a pipeline to a tree', () => {
      // When
      const asTree = getPipelineAsTree(testPipeline);

      // Then
      expect(asTree.id).to.be('CSV splitter filter');
      expect(asTree.children[0].id).children.be('XSLT filter');
      expect(asTree.children[0].children[0].id).to.be('XML writer');
      expect(asTree.children[0].children[0].children[0].id).to.be('stream appender');
    });
  });
});
