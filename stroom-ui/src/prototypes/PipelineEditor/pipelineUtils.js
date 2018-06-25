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

import { findItem, findMatch, iterateNodes } from 'lib/treeUtils';

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
  pipeline.merged.links.add.forEach((l) => {
    elements[l.from].children.push(elements[l.to]);
  });

  // Figure out the root -- if a link doesn't have anything going to it then it's the root.
  const rootLinks = pipeline.merged.links.add.filter((fromLink) => {
    const toLinks = pipeline.merged.links.add.filter(l => fromLink.from === l.to);
    return toLinks.length === 0;
  });

  let rootId;
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
    rootId = rootLinks[0].from;
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
        (namesForMemoizedFunction.includes(value) ? 'must be unique name' : undefined);
    }
  } else {
    // Create a new memoized function
    namesForMemoizedFunction = getAllElementNames(pipeline);
    memoizedUniqueName = value =>
      (namesForMemoizedFunction.includes(value) ? 'must be unique name' : undefined);
  }

  return memoizedUniqueName;
};

/**
 *
 *
 *
 * @param {pipeline} pipeline
 * @param {string} parentId
 * @param {element} childDefinition
 * @param {string} name The name to give to the new element.
 */
export function createNewElementInPipeline(pipeline, parentId, childDefinition, name) {
  return {
    configStack: pipeline.configStack,
    merged: {
      properties: pipeline.merged.properties,
      elements: {
        add: pipeline.merged.elements.add.concat([
          {
            id: name,
            type: childDefinition.type,
          },
        ]),
      },
      links: {
        add: pipeline.merged.links.add
          // add the new link
          .concat([
            {
              from: parentId,
              to: name,
            },
          ]),
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
  const itemToMoveNode = findItem(pipelineAsTree, itemToMove);
  const destinationNode = findItem(pipelineAsTree, destination);

  // If either node cannot be found...bad times
  if (!itemToMoveNode || !destinationNode) {
    return false;
  }

  // If the item being dropped is a folder, and is being dropped into itself
  if (findMatch(itemToMoveNode, destinationNode)) {
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
 * Used to delete an element from a pipeline.
 *
 * @param {pipeline} pipeline Pipeline definition before the deletion
 * @param {string} itemToDelete ID of the item to delete
 * @return The updated pipeline definition.
 */
export function deleteElementInPipeline(pipeline, itemToDelete) {
  const children = getAllChildren(pipeline, itemToDelete);

  return {
    configStack: pipeline.configStack,
    merged: {
      properties: {
        add: pipeline.merged.properties.add
          .filter(p => p.element !== itemToDelete)
          .filter(p => !children.includes(p.element)),
      },
      elements: {
        add: pipeline.merged.elements.add
          .filter(e => e.id !== itemToDelete)
          .filter(e => !children.includes(e.id)),
      },
      links: {
        add: pipeline.merged.links.add
          // Remove any existing link that goes into the deleting item
          .filter(l => l.to !== itemToDelete)
          .filter(l => l.from !== itemToDelete)
          .filter(l => !children.includes(l.to)),
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
 * Checks whether the give element is active in the pipeline. I.e. does anything link to it.
 *
 * @param {pipeline} pipeline Pipeline definition
 * @param {element} elementToCheck The element to check for activity
 */
export function isActive(pipeline, elementToCheck) {
  const linksInvolvingElement = pipeline.merged.links.add.filter(element => elementToCheck.id === element.from || elementToCheck.id === element.to);
  return linksInvolvingElement.length > 0;
}
