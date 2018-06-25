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
import { getPipelineAsTree, deleteElementInPipeline, getAllChildren } from '../pipelineUtils';

import { testPipelines } from './index';

describe('Pipeline Utils', () => {
  describe('#getPipelineAsTree', () => {
    test('should convert a pipeline to a tree', () => {
      // When
      const asTree = getPipelineAsTree(testPipelines.simple);

      // Then
      expectsForSimplePipeline(asTree);
    });
    test('should convert a pipeline to a tree and detect the correct root', () => {
      // Given
      // Swap some entities over -- it shouldn't matter if they're not in the correct order
      const testPipeline = testPipelines.simple;
      const first = testPipeline.merged.links.add[0];
      testPipeline.merged.links.add[0] = testPipeline.merged.links.add[2];
      testPipeline.merged.links.add[2] = first;

      // When
      const asTree = getPipelineAsTree(testPipeline);

      // Then
      expectsForSimplePipeline(asTree);
    });

    test('should convert a pipeline to a single node tree -- tests edge case of no links', () => {
      // When
      const asTree = getPipelineAsTree(testPipelines.singleElement);

      // Then
      expect(asTree.uuid).toBe('Source');
    });
  });

  describe('#getAllChildren', () => {
    test('should recursively return children #1', () => {
      // When
      // TODO change name to `getAllChildren` or something similar.
      const children = getAllChildren(testPipelines.forkedPipeline, 'xsltFilter');

      // Then
      expect(children.length).toBe(4);
      expectsForGetDescendants(children);
    });
    test('should recursively return children #2', () => {
      // When
      const children = getAllChildren(testPipelines.forkedPipeline, 'dsParser');

      // Then
      expect(children.length).toBe(5);
      expectsForGetDescendants(children);
      expect(children.includes('xsltFilter')).toBeTruthy();
    });
    test('should recursively return children #3', () => {
      // When
      const children = getAllChildren(testPipelines.forkedPipeline, 'xmlWriter1');

      // Then
      expect(children.length).toBe(1);
      expect(children.includes('streamAppender1')).toBeTruthy();
    });
  });

  describe('#deleteElementInPipeline', () => {
    test('should delete element and everything after', () => {
      const itemToDelete = 'xmlWriter1';
      const childToBeDeleted = 'streamAppender1';
      const parentToBePresent = 'xsltFilter';

      // When
      const newPipeline = deleteElementInPipeline(testPipelines.forkedPipeline, itemToDelete);

      // Then...
      // ... for properties
      // TODO I'm not sure we want to clear properties
      expect(newPipeline.merged.properties.add.length).toBe(2);
      expect(newPipeline.merged.properties.add[0].element).toBe(parentToBePresent);
      expectMissing(newPipeline, 'properties', 'element', childToBeDeleted);
      expectMissing(newPipeline, 'properties', 'element', itemToDelete);

      // ... for elements
      expect(newPipeline.merged.elements.add.length).toBe(5);
      expect(newPipeline.merged.elements.add[0].id).toBe(parentToBePresent);
      expectMissing(newPipeline, 'elements', 'id', childToBeDeleted);
      expectMissing(newPipeline, 'elements', 'id', itemToDelete);

      // ... for links
      expect(newPipeline.merged.links.add.length).toBe(4);
      expect(newPipeline.merged.links.add[0].from).toBe(parentToBePresent);
      expectMissing(newPipeline, 'links', 'from', childToBeDeleted);
      expectMissing(newPipeline, 'links', 'from', itemToDelete);
    });
    test('should delete element and everything after #1', () => {
      const itemToDelete = 'xsltFilter';
      const childToBeDeleted1 = 'streamAppender1';
      const childToBeDeleted2 = 'streamAppender2';
      const childToBeDeleted3 = 'xmlWriter1';
      const childToBeDeleted4 = 'xmlWriter2';
      const parentToBePresent = 'dsParser';

      // When
      const newPipeline = deleteElementInPipeline(testPipelines.forkedPipeline, itemToDelete);

      // Then...
      // ... for properties
      expect(newPipeline.merged.properties.add.length).toBe(1);
      expect(newPipeline.merged.properties.add[0].element).toBe(parentToBePresent);
      expectMissing(newPipeline, 'properties', 'element', childToBeDeleted1);
      expectMissing(newPipeline, 'properties', 'element', childToBeDeleted2);
      expectMissing(newPipeline, 'properties', 'element', childToBeDeleted3);
      expectMissing(newPipeline, 'properties', 'element', childToBeDeleted4);
      expectMissing(newPipeline, 'properties', 'element', itemToDelete);

      // ... for elements
      expect(newPipeline.merged.elements.add.length).toBe(2);
      expect(newPipeline.merged.elements.add[0].id).toBe(parentToBePresent);
      expectMissing(newPipeline, 'elements', 'id', childToBeDeleted1);
      expectMissing(newPipeline, 'elements', 'id', childToBeDeleted2);
      expectMissing(newPipeline, 'elements', 'id', childToBeDeleted3);
      expectMissing(newPipeline, 'elements', 'id', childToBeDeleted4);
      expectMissing(newPipeline, 'elements', 'id', itemToDelete);

      // ... for links
      expect(newPipeline.merged.links.add.length).toBe(1);
    });
  });
});

function expectMissing(pipeline, list, elementProperty, elementName) {
  const shouldBeEmpty = pipeline.merged[list].add.filter(p => p[elementProperty] === elementName);
  expect(shouldBeEmpty.length).toBe(0);
}

function expectsForSimplePipeline(asTree) {
  expect(asTree.uuid).toBe('Source');
  expect(asTree.children[0].uuid).toBe('dsParser');
  expect(asTree.children[0].children[0].uuid).toBe('xsltFilter');
  expect(asTree.children[0].children[0].children[0].uuid).toBe('xmlWriter');
  expect(asTree.children[0].children[0].children[0].children[0].uuid).toBe('streamAppender');
}

function expectsForGetDescendants(children) {
  expect(children.includes('xmlWriter1')).toBeTruthy();
  expect(children.includes('xmlWriter2')).toBeTruthy();
  expect(children.includes('streamAppender1')).toBeTruthy();
  expect(children.includes('streamAppender2')).toBeTruthy();
}
