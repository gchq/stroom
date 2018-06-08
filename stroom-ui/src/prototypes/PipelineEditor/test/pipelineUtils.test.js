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
import rewire from 'rewire';

import { getPipelineAsTree, deleteElementInPipeline } from '../pipelineUtils';

const pipelineUtils = rewire('../pipelineUtils');
const getChildren = pipelineUtils.__get__('getChildren');

import { testPipeline, singleElementTestPipeline } from './pipeline.testData';

describe('Pipeline Utils', () => {
  describe('#getPipelineAsTree', () => {
    test('should convert a pipeline to a tree', () => {
      // When
      const asTree = getPipelineAsTree(testPipeline);

      // Then
      expectsForTestPipeline(asTree);
    });
    test('should convert a pipeline to a tree and detect the correct root', () => {
      // Given
      // Swap some entities over -- it shouldn't matter if they're not in the correct order
      const first = testPipeline.links.add[0];
      testPipeline.links.add[0] = testPipeline.links.add[2];
      testPipeline.links.add[2] = first;

      // When
      const asTree = getPipelineAsTree(testPipeline);

      // Then
      expectsForTestPipeline(asTree);
    });

    test(
      'should convert a pipeline to a single node tree -- tests edge case of no links',
      () => {
        // When
        const asTree = getPipelineAsTree(singleElementTestPipeline);

        // Then
        expect(asTree.uuid).toBe('CSV splitter filter');
      }
    );
  });

  describe('#getChildren', () => {
    test('should recursively return children #1', () => {
      // When
      // TODO change name to `getAllChildren` or something similar.
      const children = getChildren(testPipeline, 'XSLT filter');

      // Then
      expect(children.length).toBe(4);
      expectsForGetChildren(children);
    });
    test('should recursively return children #2', () => {
      // When
      const children = getChildren(testPipeline, 'CSV splitter filter');

      // Then
      expect(children.length).toBe(5);
      expectsForGetChildren(children);
      expect(children.includes('XSLT filter')).toBeTruthy();
    });
    test('should recursively return children #3', () => {
      // When
      const children = getChildren(testPipeline, 'XML writer 1');

      // Then
      expect(children.length).toBe(1);
      expect(children.includes('stream appender 1')).toBeTruthy();
    });
  });

  describe('#deleteElementInPipeline', () => {
    test('should delete element and everything after', () => {
      const itemToDelete = 'XML writer 1';
      const childToBeDeleted = 'stream appender 1';
      const parentToBePresent = 'CSV splitter filter';

      // When
      // TODO change name to `getAllChildren` or something similar.
      const newPipeline = deleteElementInPipeline(testPipeline, itemToDelete);

      // Then...
      // ... for properties
      expect(newPipeline.properties.add.length).toBe(4);
      expect(newPipeline.properties.add[0].element).toBe(parentToBePresent);
      expectMissing(newPipeline, 'properties', 'element', childToBeDeleted);
      expectMissing(newPipeline, 'properties', 'element', itemToDelete);

      // ... for elements
      expect(newPipeline.elements.add.length).toBe(4);
      expect(newPipeline.elements.add[0].id).toBe(parentToBePresent);
      expectMissing(newPipeline, 'elements', 'id', childToBeDeleted);
      expectMissing(newPipeline, 'elements', 'id', itemToDelete);

      // ... for links
      expect(newPipeline.links.add.length).toBe(3);
      expect(newPipeline.links.add[0].from).toBe(parentToBePresent);
      expectMissing(newPipeline, 'links', 'from', childToBeDeleted);
      expectMissing(newPipeline, 'links', 'from', itemToDelete);
    });
    test('should delete element and everything after #1', () => {
      const itemToDelete = 'XSLT filter';
      const childToBeDeleted1 = 'stream appender 1';
      const childToBeDeleted2 = 'stream appender 2';
      const childToBeDeleted3 = 'XML writer 1';
      const childToBeDeleted4 = 'XML writer 2';
      const parentToBePresent = 'CSV splitter filter';

      // When
      // TODO change name to `getAllChildren` or something similar.
      const newPipeline = deleteElementInPipeline(testPipeline, itemToDelete);

      // Then...
      // ... for properties
      expect(newPipeline.properties.add.length).toBe(1);
      expect(newPipeline.properties.add[0].element).toBe(parentToBePresent);
      expectMissing(newPipeline, 'properties', 'element', childToBeDeleted1);
      expectMissing(newPipeline, 'properties', 'element', childToBeDeleted2);
      expectMissing(newPipeline, 'properties', 'element', childToBeDeleted3);
      expectMissing(newPipeline, 'properties', 'element', childToBeDeleted4);
      expectMissing(newPipeline, 'properties', 'element', itemToDelete);

      // ... for elements
      expect(newPipeline.elements.add.length).toBe(1);
      expect(newPipeline.elements.add[0].id).toBe(parentToBePresent);
      expectMissing(newPipeline, 'elements', 'id', childToBeDeleted1);
      expectMissing(newPipeline, 'elements', 'id', childToBeDeleted2);
      expectMissing(newPipeline, 'elements', 'id', childToBeDeleted3);
      expectMissing(newPipeline, 'elements', 'id', childToBeDeleted4);
      expectMissing(newPipeline, 'elements', 'id', itemToDelete);

      // ... for links
      expect(newPipeline.links.add.length).toBe(0);
    });
  });
});

function expectMissing(pipeline, list, elementProperty, elementName) {
  const shouldBeEmpty = pipeline[list].add.filter(p => p[elementProperty] === elementName);
  expect(shouldBeEmpty.length).toBe(0);
}

function expectsForTestPipeline(asTree) {
  expect(asTree.uuid).toBe('CSV splitter filter');
  expect(asTree.children[0].uuid).toBe('XSLT filter');
  expect(asTree.children[0].children[0].uuid).toBe('XML writer 1');
  expect(asTree.children[0].children[0].children[0].uuid).toBe('stream appender 1');
  expect(asTree.children[0].children[1].uuid).toBe('XML writer 2');
  expect(asTree.children[0].children[1].children[0].uuid).toBe('stream appender 2');
}

function expectsForGetChildren(children) {
  expect(children.includes('XML writer 1')).toBeTruthy();
  expect(children.includes('XML writer 2')).toBeTruthy();
  expect(children.includes('stream appender 1')).toBeTruthy();
  expect(children.includes('stream appender 2')).toBeTruthy();
}
