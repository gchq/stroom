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
  findItem,
  findMatch,
  iterateNodes
 } from 'lib/treeUtils';

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
  pipeline.elements.add.element.forEach((e) => {
    elements[e.id] = {
      uuid: e.id,
      type: e.type,
      children: [],
    };
  });

  // Create the tree using links
  pipeline.links.add.link.forEach((l) => {
    elements[l.from].children.push(elements[l.to]);
  });

  // Figure out the root
  const rootId = pipeline.links.add.link[0].from;

  return elements[rootId];
}

export const ORIENTATION = {
  horizontal : 1,
  vertical : 2
}

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
          horizontalPos : forwardPosition,
          verticalPos : sidewayPosition
        };
        break;
      case ORIENTATION.vertical:
        layoutInformation[node.uuid] = {
          horizontalPos : sidewayPosition,
          verticalPos : forwardPosition
        };
        break;
    }    
  });

  return layoutInformation;
}

export function canMovePipelineElement(pipeline, pipelineAsTree, itemToMove, destination) {
  let itemToMoveNode = findItem(pipelineAsTree, itemToMove);
  let destinationNode = findItem(pipelineAsTree, destination);

  // If the item being dropped is a folder, and is being dropped into itself
  if (findMatch(itemToMoveNode, destinationNode)) {
    return false;
  }
  if (!!itemToMoveNode.children && (itemToMoveNode.uuid === destinationNode.uuid)) {
      return false;
  }

  // Does this item appear in the destination folder already?
  return destinationNode.children
      .map(c => c.uuid)
      .filter(u => u === itemToMoveNode.uuid).length === 0;
}

export function moveElementInPipeline(pipeline, itemToMove, destination) {
  return {
    properties : pipeline.properties,
    elements : pipeline.elements,
    links : {
      add : {
        link: pipeline.links.add.link
        // Remove any existing link that goes into the moving item
        .filter(l => (l.to !== itemToMove))
        // add the new link
        .concat([
          {
            from : destination,
            to : itemToMove
          }
        ])
      }
    }
  }
}
