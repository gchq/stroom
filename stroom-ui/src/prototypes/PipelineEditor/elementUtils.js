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

import _ from 'lodash';

import { ElementRoles } from './ElementRoles';

/**
 * This will take in the map of element types and return a new map
 * of those types grouped by category
 * @param {elementTypes} elements The element definitions from Stroom
 */
export function groupByCategory(elements) {
  return Object.entries(elements)
    .map(k => k[1])
    .reduce(
      (acc, next) => ({
        ...acc,
        [next.category]: [...(acc[next.category] || []), next],
      }),
      {},
    );
}

/**
 * This function determins if a particular child element type can be connected
 * to a parent type. It takes into the account the roles being played by both
 * elements, and the number of existing connections.
 *
 * @param {PipelineElementType} parentType The parent element type
 * @param {PipelineElementType} childType The child element type
 * @param {integer} currentChildCount Number of existing connections from the parent
 */
export function isValidChildType(parentType, childType, currentChildCount) {
  if (parentType.roles.includes(ElementRoles.WRITER)) {
    if (currentChildCount > 0) {
      return false;
    }
    return childType.roles.includes(ElementRoles.DESTINATION);
  }

  if (parentType.roles.includes(ElementRoles.DESTINATION)) {
    return false;
  }

  if (parentType.roles.includes(ElementRoles.SOURCE)) {
    return (
      childType.roles.includes(ElementRoles.DESTINATION) ||
      childType.roles.includes(ElementRoles.READER) ||
      childType.roles.includes(ElementRoles.PARSER)
    );
  }

  if (parentType.roles.includes(ElementRoles.READER)) {
    return (
      childType.roles.includes(ElementRoles.DESTINATION) ||
      childType.roles.includes(ElementRoles.READER) ||
      childType.roles.includes(ElementRoles.PARSER)
    );
  }

  if (parentType.roles.includes(ElementRoles.PARSER)) {
    return (
      !childType.roles.includes(ElementRoles.READER) &&
      !childType.roles.includes(ElementRoles.PARSER) &&
      !childType.roles.includes(ElementRoles.DESTINATION)
    );
  }

  if (parentType.roles.includes(ElementRoles.TARGET)) {
    if (!parentType.roles.includes(ElementRoles.WRITER)) {
      return (
        !childType.roles.includes(ElementRoles.READER) &&
        !childType.roles.includes(ElementRoles.PARSER) &&
        !childType.roles.includes(ElementRoles.DESTINATION)
      );
    }

    return (
      !childType.roles.includes(ElementRoles.READER) &&
      !childType.roles.includes(ElementRoles.PARSER)
    );
  }

  return !childType.roles.includes(ElementRoles.DESTINATION);
}
