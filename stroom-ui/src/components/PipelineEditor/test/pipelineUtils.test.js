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
import {
  getPipelineAsTree,
  getBinItems,
  createNewElementInPipeline,
  reinstateElementToPipeline,
  removeElementFromPipeline,
  getAllChildren,
  setElementPropertyValueInPipeline,
  getParentProperty
} from '../pipelineUtils';

import {
  keyByType
} from '../elementUtils';

import { testPipelines, elements } from './index';

const elementsByType = keyByType(elements);

describe('Pipeline Utils', () => {
  describe('#getPipelineAsTree', () => {
    test('should convert a simple pipeline to a tree', () => {
      // When
      const asTree = getPipelineAsTree(testPipelines.simple);

      // Then
      expectsForSimplePipeline(asTree);
    });
    test('should convert a multi branch child pipeline to a tree', () => {
      // When
      const asTree = getPipelineAsTree(testPipelines.multiBranchChild);

      // Then
      //console.log('Multi Branch Child', JSON.stringify(asTree, null, 2));
    })
    test('should convert a pipeline to a tree and detect the correct root', () => {
      // Given
      // Swap some entities over -- it shouldn't matter if they're not in the correct order
      const testPipeline = {
        configStack: testPipelines.simple.configStack,
        merged: {
          ...testPipelines.simple.merged,
          links: {
            add: [...testPipelines.simple.merged.links.add],
          },
        },
      };
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

  describe('#createNewElementInPipeline', () => {
    test('should add item to merged and config stack', () => {
      // Given
      const testPipeline = testPipelines.simple;
      const elementDefinition = elements.find(f => f.type === 'XSLTFilter');
      const newElementName = 'New XSLT Filter';
      const parentId = 'dsParser';

      // When
      const updatedPipeline = createNewElementInPipeline(
        testPipeline,
        parentId,
        elementDefinition,
        newElementName,
      );

      // Then
      const arrayContainingElement = expect.arrayContaining([
        {
          id: newElementName,
          type: elementDefinition.type,
        },
      ]);
      const arrayContainingLink = expect.arrayContaining([
        {
          from: parentId,
          to: newElementName,
        },
      ]);

      expect(updatedPipeline.configStack[0].elements.add).toEqual(arrayContainingElement);
      expect(updatedPipeline.configStack[0].links.add).toEqual(arrayContainingLink);

      expect(updatedPipeline.merged.elements.add).toEqual(arrayContainingElement);
      expect(updatedPipeline.merged.links.add).toEqual(arrayContainingLink);
    });
  });

  describe('#setElementPropertyValueInPipeline', () => {

    test('should update a property on an element in the config stack', () => {
      // Given
      const testPipeline = Object.assign(testPipelines.simple, {});
      const elementName = 'xsltFilter'
      const propertyName = 'xslt'
      const propertyType = 'entity'
      const propertyEntityValue = {
        type: 'some type',
        uuid: 'some uuid',
        name: 'some name',
      }

      // When
      const updatedPipeline = setElementPropertyValueInPipeline(
        testPipeline,
        elementName,
        propertyName,
        propertyType,
        propertyEntityValue,
      );

      // Then
      const propertyValue = {
        boolean: null,
        entity: propertyEntityValue,
        integer: null,
        long: null,
        string: null
      }
      const stackAdd = updatedPipeline.configStack[0].properties.add;
      expectsForNewProperties(stackAdd, 2, elementName, propertyName, propertyValue);
      const mergedAdd = updatedPipeline.merged.properties.add;
      expectsForNewProperties(mergedAdd, 2, elementName, propertyName, propertyValue);
    });

    test('should add a property to an element in the config stack', () => {
      // Given
      const testPipeline = Object.assign(testPipelines.simple, {});
      const elementName = 'xsltFilter'
      const propertyName = 'xsltNamePattern'
      const propertyType = 'string'
      const propertyEntityValue = 'New value'

      // When
      const updatedPipeline = setElementPropertyValueInPipeline(
        testPipeline,
        elementName,
        propertyName,
        propertyType,
        propertyEntityValue,
      );

      // Then
      const propertyValue = {
        boolean: null,
        entity: null,
        integer: null,
        long: null,
        string: propertyEntityValue
      }
      const stackAdd = updatedPipeline.configStack[0].properties.add;
      expectsForNewProperties(stackAdd, 3, elementName, propertyName, propertyValue);
      const mergedAdd = updatedPipeline.merged.properties.add;
      expectsForNewProperties(mergedAdd, 3, elementName, propertyName, propertyValue);
    });
  });



  describe('#reinstateElementToPipeline', () => {
    test('it should restore an element and add a link to the correct parent', () => {
      // Given
      const testPipeline = testPipelines.forkRemoved;
      const parentId = 'dsParser';
      const itemToReinstate = testPipelines.forkRemoved.configStack[0].elements.remove[0];

      // When
      const updatedPipeline = reinstateElementToPipeline(testPipeline, parentId, itemToReinstate);
      const updatedConfigStackThis = updatedPipeline.configStack[0];

      // Then
      const expectedLink = {
        from: parentId,
        to: itemToReinstate.id
      }

      expect(updatedConfigStackThis.elements.remove.length).toBe(0);
      expect(updatedConfigStackThis.links.add).toEqual(expect.arrayContaining([expectedLink]))
    })
  });

  describe('#removeElementFromPipeline', () => {
    test('should hide an element and link that are inherited', () => {
      // Given
      const testPipeline = testPipelines.inherited;
      const itemToDelete = 'pXmlWriter';
      const configStackThis = testPipeline.configStack[1];

      // When
      const updatedPipeline = removeElementFromPipeline(testPipeline, itemToDelete);
      const updatedConfigStackThis = updatedPipeline.configStack[1];

      // Then
      // Check merged - elements
      expect(testPipeline.merged.elements.add.map(e => e.id).includes(itemToDelete)).toBeTruthy();
      expect(updatedPipeline.merged.elements.add.map(e => e.id).includes(itemToDelete)).toBeFalsy();

      // Check merged - links
      expect(testPipeline.merged.links.add.map(l => l.to).includes(itemToDelete)).toBeTruthy();
      expect(updatedPipeline.merged.links.add.map(l => l.to).includes(itemToDelete)).toBeFalsy();

      // Check config stack - elements
      expect(configStackThis.elements.add.map(e => e.id).includes(itemToDelete)).toBeFalsy();
      expect(configStackThis.elements.remove.map(e => e.id).includes(itemToDelete)).toBeFalsy();
      expect(updatedConfigStackThis.elements.add.map(e => e.id).includes(itemToDelete)).toBeFalsy();
      expect(updatedConfigStackThis.elements.remove.map(e => e.id).includes(itemToDelete)).toBeTruthy();

      // Check config stack - links
      expect(configStackThis.links.add.map(l => l.to).includes(itemToDelete)).toBeFalsy();
      expect(configStackThis.links.remove.map(l => l.to).includes(itemToDelete)).toBeFalsy();
      expect(updatedConfigStackThis.links.add.map(l => l.to).includes(itemToDelete)).toBeFalsy();
      expect(updatedConfigStackThis.links.remove.map(l => l.to).includes(itemToDelete)).toBeTruthy();

      // Check that a follow on element & link are still just being added to merged picture
      expect(updatedPipeline.merged.elements.add.map(e => e.id).includes('pStreamAppender')).toBeTruthy();
      expect(updatedPipeline.merged.links.add).toEqual(expect.arrayContaining([
        {
          from: 'pXmlWriter',
          to: 'pStreamAppender',
        },
      ]));
    });
    test('should hide an element that is inherited, but delete a link that is ours', () => {
      // Given
      const testPipeline = testPipelines.childRestoredLink;
      const itemToDelete = 'xsltFilter'; // is a parent element, but the link was restored to an element we created
      const itemToDeleteParent = 'xmlParser'; // this is our element which we restored the parent element onto
      const itemToDeleteChild = 'xmlWriter'; // this is the element that are deleted element used as an output
      const configStackThis = testPipeline.configStack[1];

      // When
      const updatedPipeline = removeElementFromPipeline(testPipeline, itemToDelete);
      const updatedConfigStackThis = updatedPipeline.configStack[1];

      // Then
      // Check merged elements
      expect(updatedPipeline.merged.elements.remove.map(e => e.id).includes(itemToDelete)).toBeFalsy();

      // Check merged links
      const testLink = [
        {
          from: itemToDeleteParent,
          to: itemToDelete,
        },
      ];
      expect(testPipeline.merged.links.add).toEqual(expect.arrayContaining(testLink));
      expect(updatedPipeline.merged.links.add).not.toEqual(expect.arrayContaining(testLink));

      const testFollowOnLink = [{
        from: itemToDelete,
        to: itemToDeleteChild
      }]
      expect(testPipeline.merged.links.add).toEqual(expect.arrayContaining(testFollowOnLink));
      expect(updatedPipeline.merged.links.add).toEqual(expect.arrayContaining(testFollowOnLink));

      // Check merged elements
      expect(testPipeline.merged.elements.add.map(e => e.id).includes(itemToDelete)).toBeTruthy();
      expect(testPipeline.merged.elements.add.map(e => e.id).includes(itemToDeleteChild)).toBeTruthy();

      expect(updatedPipeline.merged.elements.add.map(e => e.id).includes(itemToDelete)).toBeFalsy();
      expect(updatedPipeline.merged.elements.add.map(e => e.id).includes(itemToDeleteChild)).toBeTruthy();
    });
    test('should be able to get a pipeline as a tree after deletion', () => {
      // Given
      const testPipeline = testPipelines.multiBranchChild;
      const itemToDelete = 'xmlWriter1';
      const configStackThis = testPipeline.configStack[1];

      // When
      const updatedPipeline = removeElementFromPipeline(testPipeline, itemToDelete);
      const updatedConfigStackThis = updatedPipeline.configStack[1];

      const asTree = getPipelineAsTree(updatedPipeline);
      const recycleBin = getBinItems(updatedPipeline, elementsByType);

    });
    test('should hide an element that is ours, and delete the link', () => {
      // Given
      const testPipeline = testPipelines.simple;
      const itemToDelete = 'xmlWriter';
      const configStackThis = testPipeline.configStack[0];

      // When
      const updatedPipeline = removeElementFromPipeline(testPipeline, itemToDelete);
      const updatedConfigStackThis = updatedPipeline.configStack[0];

      // Then
      // Check merged - elements
      expect(testPipeline.merged.elements.add.map(e => e.id).includes(itemToDelete)).toBeTruthy();
      expect(updatedPipeline.merged.elements.add.map(e => e.id).includes(itemToDelete)).toBeFalsy();

      // Check merged - links
      expect(testPipeline.merged.links.add.map(l => l.to).includes(itemToDelete)).toBeTruthy();
      expect(updatedPipeline.merged.links.add.map(l => l.to).includes(itemToDelete)).toBeFalsy();

      // Check Config Stack - elements
      expect(configStackThis.elements.add.map(e => e.id).includes(itemToDelete)).toBeTruthy();
      expect(configStackThis.elements.remove.map(e => e.id).includes(itemToDelete)).toBeFalsy();
      expect(updatedConfigStackThis.elements.add.map(e => e.id).includes(itemToDelete)).toBeFalsy();
      expect(updatedConfigStackThis.elements.remove.map(e => e.id).includes(itemToDelete)).toBeTruthy();

      // Check Config stack - links
      expect(configStackThis.links.add.map(l => l.to).includes(itemToDelete)).toBeTruthy();
      expect(configStackThis.links.remove.map(l => l.to).includes(itemToDelete)).toBeFalsy();
      expect(updatedConfigStackThis.links.add.map(l => l.to).includes(itemToDelete)).toBeFalsy();
      expect(updatedConfigStackThis.links.remove.map(l => l.to).includes(itemToDelete)).toBeFalsy();

      // Check that a follow on element & link are still just being added to merged picture
      expect(updatedPipeline.merged.elements.add.map(e => e.id).includes('streamAppender')).toBeTruthy();
      expect(updatedPipeline.merged.links.add).toEqual(expect.arrayContaining([
        {
          from: 'xmlWriter',
          to: 'streamAppender',
        },
      ]));
    });
  });

  describe('#getParentProperty', () => {
    test('shouldn\'t find anything because there\'s no parent', () => {
      // Given
      const pipeline = testPipelines.noParent;
      // When
      const parentProperty = getParentProperty(pipeline.configStack, 'xsltFilter', 'xsltNamePattern');

      // Then
      expect(parentProperty).toBe(undefined);
    });

    test('shouldn\'t find anything because there\'s nothing in the parent', () => {
      // Given
      const pipeline = testPipelines.parentNoProperty;
      // When
      const parentProperty = getParentProperty(pipeline.configStack, 'combinedParser', 'type');
      // Then
      expect(parentProperty).toBe(undefined);
    });
    test('should find parent property', () => {
      // Given
      const pipeline = testPipelines.parentWithProperty;
      // When
      const parentProperty = getParentProperty(pipeline.configStack, 'combinedParser', 'type');
      const parentProperty2 = getParentProperty(pipeline.configStack, 'xsltFilter', 'xsltNamePattern');
      // Then
      expect(parentProperty.element).toBe('combinedParser');
      expect(parentProperty.name).toBe('type')
      expect(parentProperty.value.string).toBe('JS')
      expect(parentProperty2.element).toBe('xsltFilter');
      expect(parentProperty2.name).toBe('xsltNamePattern')
      expect(parentProperty2.value.string).toBe('DSD')
    });

    test('shouldn\' find a property in parent or parent\'s parent', () => {
      // Given
      const pipeline = testPipelines.parentNoPropertyParentNoProperty;
      // When
      const parentProperty = getParentProperty(pipeline.configStack, 'combinedParser', 'type');
      // Then
      expect(parentProperty).toBe(undefined);
    });

    test('should find a property, in the parent\'s parent', () => {
      // Given
      const pipeline = testPipelines.parentNoPropertyParentWithProperty;
      // When
      const parentProperty = getParentProperty(pipeline.configStack, 'xsltFilter', 'property1');
      // Then
      expect(parentProperty.element).toBe('xsltFilter');
      expect(parentProperty.name).toBe('property1')
      expect(parentProperty.value.boolean).toBe(false)
    });

    test('should find a property, in the parent but not their parent', () => {
      // Given
      const pipeline = testPipelines.parentWithPropertyParentNoProperty;
      // When
      const parentProperty = getParentProperty(pipeline.configStack, 'xsltFilter', 'property1');
      // Then
      expect(parentProperty.element).toBe('xsltFilter');
      expect(parentProperty.name).toBe('property1')
      expect(parentProperty.value.boolean).toBe(false)
    });

    test('should find a property in the parent and ignore a property in their parent', () => {
      // Given
      const pipeline = testPipelines.parentWithPropertyParentWithProperty;
      // When
      const parentProperty = getParentProperty(pipeline.configStack, 'xsltFilter', 'xsltNamePattern');
      // Then
      expect(parentProperty.element).toBe('xsltFilter');
      expect(parentProperty.name).toBe('xsltNamePattern')
      expect(parentProperty.value.string).toBe('DSD')
    });

    test('shouldn\'t find a property in the parent because although it\'s there it also exists in \'remove\'', () => {
      // Given
      const pipeline = testPipelines.parentWithRemoveforItsParentsAdd;
      // When
      const parentProperty = getParentProperty(pipeline.configStack, 'xsltFilter', 'property2');
      // Then
      expect(parentProperty).toBe(undefined);
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

function expectsForNewProperties(properties, expectedSize, elementName, propertyName, propertyValue){
  expect(properties.length).toEqual(expectedSize)
  const property = properties.find(element => element.element === elementName && element.name == propertyName);
  expect(property.element).toEqual(elementName);
  expect(property.name).toEqual(propertyName);
  expect(property.value).toEqual(propertyValue);
}
