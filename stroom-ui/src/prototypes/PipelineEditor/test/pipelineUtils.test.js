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
import expect from 'expect';

import { getPipelineAsTree } from '../pipelineUtils';

import { testPipeline } from './pipeline.testData';

describe('Pipeline Utils', () => {
  describe('#getPipelineAsTree', () => {
    it('should convert a pipeline to a tree', () => {
      // When
      const asTree = getPipelineAsTree(testPipeline);

      // Then
      expect(asTree.uuid).toBe('CSV splitter filter');
      expect(asTree.children[0].uuid).toBe('XSLT filter');
      expect(asTree.children[0].children[0].uuid).toBe('XML writer 1');
      expect(asTree.children[0].children[0].children[0].uuid).toBe('stream appender 1');
      expect(asTree.children[0].children[1].uuid).toBe('XML writer 2');
      expect(asTree.children[0].children[1].children[0].uuid).toBe('stream appender 2');
    });
  });
});
