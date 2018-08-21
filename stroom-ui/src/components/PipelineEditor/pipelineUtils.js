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

import { findItem, itemIsInSubtree, iterateNodes } from 'lib/treeUtils';

export function getBinItems(pipeline, elementsByType) {
  const thisConfigStack = pipeline.configStack[pipeline.configStack.length - 1];

  return thisConfigStack.elements.remove.map(e => ({
    recycleData: e,
    element: elementsByType[e.type],
  }));
}

/**
 * This function takes the denormalised pipeline definition and creates
 * a tree like structure. The root element is the 'from' of the first link.
 * This tree structure can then be used to lay the elements out in the graphical display.
 *
 * @param {object} pipeline
 * @return {object} Tree like structure
 */
export function getPipelineAsTree(pipeline) {
  const elements = {};

  // Put all the elements into an object, keyed on id
  pipeline.merged.elements.add.forEach((e) => {
    elements[e.id] = {
      uuid: e.id,
      type: e.type,
      children: [],
    };
  });

  // Create the tree using links
  pipeline.merged.links.add.filter(l => !!elements[l.from]).forEach((l) => {
    elements[l.from].children.push(elements[l.to]);
  });

  // Figure out the root
  let rootId;

  // First, is there an element of type Source?
  if (elements.Source) {
    rootId = 'Source';
  } else {
    // if a link doesn't have anything going to it then it may be a root.
    const rootLinks = pipeline.merged.links.add.filter((fromLink) => {
      const toLinks = pipeline.merged.links.add.filter(l => fromLink.from === l.to);
      return toLinks.length === 0;
    });

    if (rootLinks.length === 0) {
      // Maybe there's only one thing and therefore no links?
      if (pipeline.merged.elements.add.length !== 0) {
        // If there're no links then we can use the first element.
        rootId = pipeline.merged.elements.add[0].id;
      } else {
        // If there are no elements then we can't have a root node.
        rootId = undefined;
      }
    } else {
      // Just pick the first potential root? (not sure about this)
      rootId = rootLinks[0].from;
    }
  }

  return rootId ? elements[rootId] : undefined;
}

export const ORIENTATION = {
  horizontal: 1,
  vertical: 2,
};

/**
 * This calculates the layout information for the elements in a tree where the tree
 * must be layed out in a graphical manner.
 * The result object only indicates horizontal and vertical positions as 1-up integers.
 * A further mapping is required to convert this to specific layout pixel information.
 *
 * @param {treeNode} asTree The pipeline information as a tree with UUID's and 'children' on nodes.
 * @return {object} An object with a key for each UUID in the tree. The values are objects with
 * the following properties {horizontalPos, verticalPos}. These position indicators are just
 * 1-up integer values that can then bo converted to specific layout information (pixel position, position in grid etc)
 */
export function getPipelineLayoutInformation(asTree, orientation = ORIENTATION.horizontal) {
  const layoutInformation = {};

  let sidewayPosition = 1;
  let lastLineageLengthSeen = -1;
  iterateNodes(asTree, (lineage, node) => {
    const forwardPosition = lineage.length;

    if (forwardPosition <= lastLineageLengthSeen) {
      sidewayPosition += 1;
    }
    lastLineageLengthSeen = forwardPosition;

    switch (orientation) {
      case ORIENTATION.horizontal:
        layoutInformation[node.uuid] = {
          horizontalPos: forwardPosition,
          verticalPos: sidewayPosition,
        };
        break;
      case ORIENTATION.vertical:
        layoutInformation[node.uuid] = {
          horizontalPos: sidewayPosition,
          verticalPos: forwardPosition,
        };
        break;
      default:
        throw new Error(`Invalid orientation value: ${orientation}`);
    }
  });

  return layoutInformation;
}

/**
 * Use this function to retrieve all element names from the pipeline.
 *
 * @param {pipeline} pipeline The pipeline from with to extract names
 */
export function getAllElementNames(pipeline) {
  const names = [];

  pipeline.merged.elements.add
    .map(e => e.id)
    .map(id => id.toLowerCase())
    .forEach(id => names.push(id));
  pipeline.configStack.forEach((cs) => {
    cs.elements.add
      .map(e => e.id)
      .map(id => id.toLowerCase())
      .forEach(id => names.push(id));
  });

  return names;
}

// We have to 'cache' the unique name function or the redux form remounts every time the value changes.
let memoizedUniqueName;
let namesForMemoizedFunction;

const listsOfStringsAreEquals = (list1, list2) => {
  if (list1.length !== list2.length) {
    return false;
  }

  return JSON.stringify(list1) === JSON.stringify(list2);
};

export const uniqueElementName = (pipeline) => {
  // If we already had a names list
  if (namesForMemoizedFunction) {
    const namesNow = getAllElementNames(pipeline);

    // Has it changed?
    if (!listsOfStringsAreEquals(namesForMemoizedFunction, namesNow)) {
      namesForMemoizedFunction = namesNow;
      memoizedUniqueName = value =>
        (namesForMemoizedFunction.includes(value.toLowerCase()) ? 'must be unique name' : undefined);
    }
  } else {
    // Create a new memoized function
    namesForMemoizedFunction = getAllElementNames(pipeline);
    memoizedUniqueName = value =>
      (namesForMemoizedFunction.includes(value.toLowerCase()) ? 'must be unique name' : undefined);
  }

  return memoizedUniqueName;
};

/**
 * Utility function for replacing the last item in an array by using a given map function.
 * All the other items are left as they are.
 */
function mapLastItemInArray(input, mapFunc) {
  return input.map((item, i, arr) => (i === arr.length - 1 ? mapFunc(item) : item));
}

/**
 * Adds a new entry to a pipeline for the chosen element definition.
 * Creates the link between the new element and the given parent.
 * The new element will be given the name as it's unique ID.
 *
 * @param {pipeline} pipeline The current total picture of the pipeline
 * @param {string} parentId The ID of the parent that the link is being added to.
 * @param {element} childDefinition The definiton of the new element.
 * @param {string} name The name to give to the new element.
 */
export function createNewElementInPipeline(pipeline, parentId, childDefinition, name) {
  const newElement = {
    id: name,
    type: childDefinition.type,
  };
  const newLink = {
    from: parentId,
    to: name,
  };

  return {
    ...pipeline,
    configStack: mapLastItemInArray(pipeline.configStack, stackItem => ({
      properties: stackItem.properties,
      elements: {
        ...stackItem.elements,
        add: stackItem.elements.add.concat([newElement]),
      },
      links: {
        ...stackItem.links,
        add: stackItem.links.add.concat([newLink]),
      },
    })),
    merged: {
      properties: pipeline.merged.properties,
      elements: {
        ...pipeline.merged.elements,
        add: pipeline.merged.elements.add.concat([newElement]),
      },
      links: {
        ...pipeline.merged.links,
        add: pipeline.merged.links.add
          // add the new link
          .concat([newLink]),
      },
    },
  };
}

/**
 * Adds or updates a property on the element of a pipeline.
 *
 * @param {pipeline} pipeline The current definition of the pipeline.
 * @param {string} element The name of the element to update.
 * @param {string} name The name of the property on the element to update
 * @param {string} propertyType The type of the property to update, one of boolean, entity, integer, long, or string.
 * @param {boolean|entity|integer|long|string} propertyValue The value to add or update
 */
export function setElementPropertyValueInPipeline(
  pipeline,
  element,
  name,
  propertyType,
  propertyValue,
) {

  // Create the 'value' property.
  let value = {boolean: null, entity: null, integer: null, long: null, string: null}
  value[propertyType.toLowerCase()] = propertyValue

  const property = {
    element,
    name,
    value,
  };


  const stackAdd = pipeline.configStack[pipeline.configStack.length - 1].properties.add;
  addToProperties(stackAdd, property)

  const mergeAdd = pipeline.merged.properties.add;
  addToProperties(mergeAdd, property)

  return {
    ...pipeline,
    configStack: mapLastItemInArray(pipeline.configStack, stackItem => ({
      elements: stackItem.elements,
      links: stackItem.links,
      properties: {
        ...stackItem.properties,
        add: stackAdd,
      },
    })),
    merged: {
      elements: pipeline.merged.elements,
      links: pipeline.merged.links,
      properties: {
        ...pipeline.merged.properties,
        add: mergeAdd,
      }
    }
  };
}

function addToProperties(properties, property){
  let index = properties.findIndex(item => item.element === property.element && item.name === property.name);
  let addOrReplace; // The deleteCount param for splice, i.e. 0 for insert, 1 for replace

  if (index === -1) {
    addOrReplace = 0;
    index = properties.length; // Insert at the end
  } else {
    addOrReplace = 1;
  }

  properties.splice(index, addOrReplace, property);
}

export function revertPropertyToParent(pipeline, element, name){
  if(pipeline.configStack.length < 2) throw new Error('This function requires a configStack with a parent');
  if(element === undefined) throw new Error('This function requires an element name');
  if(name === undefined) throw new Error('This function requires a property name');

  const childAdd = pipeline.configStack[pipeline.configStack.length - 1].properties.add
  const parentAdd = pipeline.configStack[pipeline.configStack.length - 2].properties.add
  const mergedAdd = pipeline.merged.properties.add
  
  const indexToRemove = childAdd.findIndex(addItem => addItem.name === name);
  const parentProperty = parentAdd.find(addItem => addItem.name === name);
  const indexToRemoveFromMerged = mergedAdd.findIndex(addItem => addItem.name === name);

  return {
    ...pipeline,
    configStack: mapLastItemInArray(pipeline.configStack, stackItem => ({
      elements: stackItem.elements,
      links: stackItem.links,
      properties: {
        ...stackItem.properties,
        add: [
          // We want to remove the property from the child
          ...stackItem.properties.add.slice(0, indexToRemove),
          ...stackItem.properties.add.slice(indexToRemove + 1),
        ],
      },
    })),
    merged: {
      elements: pipeline.merged.elements,
      links: pipeline.merged.links,
      properties: {
        ...pipeline.merged.properties,
        add:[
          // We want to remove the property from the merged stack...
          ...pipeline.merged.properties.add.slice(0, indexToRemoveFromMerged),
          ...pipeline.merged.properties.add.slice(indexToRemoveFromMerged + 1),
          //... and replace it with the paren'ts property
          parentProperty
        ]
      }
    }
  };
}

export function revertPropertyToDefault(pipeline, element, name){
  if(pipeline.configStack.length < 2) throw new Error('This function requires a configStack with a parent');
  if(element === undefined) throw new Error('This function requires an element name');
  if(name === undefined) throw new Error('This function requires a property name');

  const childAdd = pipeline.configStack[pipeline.configStack.length - 1].properties.add
  const mergedAdd = pipeline.merged.properties.add
  const indexToRemoveFromMerged = mergedAdd.findIndex(addItem => addItem.name === name);

  // We might be removing something that has an override on a child, in which case we 
  // need to make sure we remove the child add too.
  const indexToRemove = childAdd.findIndex(addItem => addItem.name === name);
  if( indexToRemove !== -1) {
    const propertyForRemove = childAdd.find(addItem => addItem.name === name);
    return {
      ...pipeline,
      configStack: mapLastItemInArray(pipeline.configStack, stackItem => ({
        elements: stackItem.elements,
        links: stackItem.links,
        properties: {
          ...stackItem.properties,
          add: [
            // We want to remove the property from the child
            ...stackItem.properties.add.slice(0, indexToRemove),
            ...stackItem.properties.add.slice(indexToRemove + 1),
          ],
          remove: [
            ...stackItem.properties.remove,
            propertyForRemove // We add the property we found on the parent
          ]
        },
      })),
      merged: {
        elements: pipeline.merged.elements,
        links: pipeline.merged.links,
        properties: {
          ...pipeline.merged.properties,
          add: [
            // Merged shouldn't have this property at all, and it doesn't need a remove either
            ...pipeline.merged.properties.add.slice(0, indexToRemoveFromMerged),
            ...pipeline.merged.properties.add.slice(indexToRemoveFromMerged + 1),
          ]
        }
      }
    };
  } else {
    // If we don't have this property in the child then we need to get it from the parent:
    const propertyForRemove = getParentProperty(pipeline.configStack, element, name);
    return {
      ...pipeline,
      configStack: mapLastItemInArray(pipeline.configStack, stackItem => ({
        elements: stackItem.elements, 
        links: stackItem.links, 
        properties: {
          ...stackItem.properties,
          add: stackItem.properties.add, // We don't have anything to change here
          remove: [
            ...stackItem.properties.remove,
            propertyForRemove // We add the property we found on the parent
          ]
        },
      })),
      // Just copy them over
      merged: {
        elements: pipeline.merged.elements,
        links: pipeline.merged.links,
        properties: {
          ...pipeline.merged.properties,
          add: [
            // Merged shouldn't have this property at all, and it doesn't need a remove either
            ...pipeline.merged.properties.add.slice(0, indexToRemoveFromMerged),
            ...pipeline.merged.properties.add.slice(indexToRemoveFromMerged + 1),
          ]
        }
      }
    };
  }
}

/**
 * Reinstates an element into the pipeline that had previously been removed.
 *
 * @param {pipeline} pipeline The current definition of the pipeline.
 * @param {string} parentId The ID of the element that the new connection will be made from.
 * @param {object} recycleData {id, type} The identifying information for the element being re-instated
 */
export function reinstateElementToPipeline(pipeline, parentId, recycleData) {
  const newLink = {
    from: parentId,
    to: recycleData.id,
  };

  return {
    ...pipeline,
    configStack: mapLastItemInArray(pipeline.configStack, stackItem => ({
      properties: stackItem.properties,
      elements: {
        add: stackItem.elements.add.concat([recycleData]),
        remove: stackItem.elements.remove.filter(e => e.id !== recycleData.id),
      },
      links: {
        ...stackItem.links,
        add: stackItem.links.add.concat([newLink]),
      },
    })),
    merged: {
      properties: pipeline.merged.properties,
      elements: {
        add: pipeline.merged.elements.add.concat([recycleData]),
        remove: pipeline.merged.elements.remove.filter(e => e.id !== recycleData.id),
      },
      links: {
        ...pipeline.merged.links,
        add: pipeline.merged.links.add
          // add the new link
          .concat([newLink]),
      },
    },
  };
}

/**
 * Used to delete an element from a pipeline.
 *
 * @param {pipeline} pipeline Pipeline definition before the deletion
 * @param {string} itemToDelete ID of the item to delete
 * @return The updated pipeline definition.
 */
export function removeElementFromPipeline(pipeline, itemToDelete) {
  // Get hold of our entry in the config stack
  const configStackThis = pipeline.configStack[pipeline.configStack.length - 1];

  // Find the element in the merged picture (it should be there)
  const elementFromMerged = pipeline.merged.elements.add.find(e => e.id === itemToDelete);
  const linkFromMerged = pipeline.merged.links.add.find(l => l.to === itemToDelete);

  // Find the element and link from our config stack (they may not be from our stack)
  const linkIsOurs = configStackThis.links.add.find(l => l.to === itemToDelete);

  // Determine the behaviour for removing the link, based on if it was ours or not
  let calcNewLinks;
  if (linkIsOurs) {
    // we can simply filter out the add
    calcNewLinks = ourStackLinks => ({
      add: ourStackLinks.add.filter(l => l.to !== itemToDelete),
      remove: ourStackLinks.remove,
    });
  } else {
    // we will need to shadow the link we inherited
    calcNewLinks = ourStackLinks => ({
      add: ourStackLinks.add,
      remove: ourStackLinks.remove.concat([linkFromMerged]),
    });
  }

  return {
    ...pipeline,
    configStack: mapLastItemInArray(pipeline.configStack, stackItem => ({
      properties: stackItem.properties,
      elements: {
        add: stackItem.elements.add.filter(e => e.id !== itemToDelete),
        remove: stackItem.elements.remove.concat([elementFromMerged]),
      },
      links: calcNewLinks(stackItem.links),
    })),
    merged: {
      properties: pipeline.merged.properties, // leave intact
      elements: {
        ...pipeline.merged.elements,
        add: pipeline.merged.elements.add.filter(e => e.id !== itemToDelete), // simply remove itemToDelete
      },
      links: {
        ...pipeline.merged.links,
        add: pipeline.merged.links.add.filter(l => l.to !== itemToDelete), // simply remove the link where to=itemToDelete
      },
    },
  };
}

/**
 * Use to check if a pipeline element can be moved onto the destination given.
 *
 * @param {pipeline} pipeline
 * @param {treeNode} pipelineAsTree
 * @param {string} itemToMove ID of the item to move
 * @param {string} destination ID of the destination
 * @return {boolean} Indicate if the move is valid.
 */ 
export function canMovePipelineElement(pipeline, pipelineAsTree, itemToMove, destination) {
  const {node: itemToMoveNode } = findItem(pipelineAsTree, itemToMove);
  const {node: destinationNode } = findItem(pipelineAsTree, destination);

  // If either node cannot be found...bad times
  if (!itemToMoveNode || !destinationNode) {
    return false;
  }

  // If the item being dropped is a folder, and is being dropped into itself
  if (itemIsInSubtree(itemToMoveNode, destinationNode)) {
    return false;
  }
  if (!!itemToMoveNode.children && itemToMoveNode.uuid === destinationNode.uuid) {
    return false;
  }

  // Does this item appear in the destination folder already?
  return (
    destinationNode.children.map(c => c.uuid).filter(u => u === itemToMoveNode.uuid).length === 0
  );
}

/**
 * Used to carry out the move of elements then return the updated pipeline definition.
 *
 * @param {pipeline} pipeline
 * @param {string} itemToMove Id of the element to move
 * @param {string} destination Id of the destination
 * @return The updated pipeline definition.
 */
export function moveElementInPipeline(pipeline, itemToMove, destination) {
  return {
    ...pipeline,
    configStack: pipeline.configStack,
    merged: {
      properties: pipeline.merged.properties,
      elements: pipeline.merged.elements,
      links: {
        add: pipeline.merged.links.add
          // Remove any existing link that goes into the moving item
          .filter(l => l.to !== itemToMove)
          // add the new link
          .concat([
            {
              from: destination,
              to: itemToMove,
            },
          ]),
      },
    },
  };
}

/**
 * Gets an array of all descendents of the pipeline element
 *
 * @param {pipeline} pipeline Pipeline definition
 * @param {string} parent The id of the parent
 */
export function getAllChildren(pipeline, parent) {
  let allChildren = [];

  const getAllChildren = (pipeline, element) => {
    const thisElementsChildren = pipeline.merged.links.add
      .filter(p => p.from === element)
      .map(p => p.to);
    allChildren = allChildren.concat(thisElementsChildren);
    for (const childIndex in thisElementsChildren) {
      getAllChildren(pipeline, thisElementsChildren[childIndex]);
    }
  };

  getAllChildren(pipeline, parent);

  return allChildren;
}


/**
 * Looks through the parents in the stack until it finds the first time this property has been set.
 * It'll return that value but if it's never been set it'll return undefined. It won't return a value 
 * if the property exists in 'remove'.
 * 
 * @param {configStack} stack The config stack for the pipeline 
 * @param {string} elementId The elementId of the
 * @param {string} propertyName The name of the property to search for
 */
export function getParentProperty(stack, elementId, propertyName) {
  const getFromParent = (index) => {
    const property = stack[index].properties.add.find(element => element.element === elementId && element.name === propertyName);
    const removeProperty = stack[index].properties.remove.find(element => element.element === elementId && element.name === propertyName);
    if(property !== undefined){
      // We return the first matching property we find.
      return property;
    } else {
      // If we haven't found one we might need to continue looking up the stack
      // We won't continue looking up the stack if we have a matching 'remove' property.
      if( index -1 >= 0 && removeProperty === undefined){
        return getFromParent(index - 1)
      }
      else return undefined;
    }
  }

  if(stack.length < 2) return undefined;
  else return getFromParent(stack.length - 2);
}