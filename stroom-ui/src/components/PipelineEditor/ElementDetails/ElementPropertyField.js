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

import React from 'react';
import PropTypes from 'prop-types';

import AppSearchBar from 'components/AppSearchBar';

/**
 *
 * @param {property} value The property to get a field for
 * @param {string} name The name of the property
 * @param {string} pipelineId The ID of the pipeline this property's element belongs to
 * @param {string} elementId The ID of the element this property belongs to
 * @param {string} type The type of the element
 * @param {array} docRefTypes The docref types to filter by
 */
const ElementPropertyField = ({
  pipelineElementPropertyUpdated,
  value,
  name,
  pipelineId,
  elementId,
  type,
  docRefTypes,
}) => {
  let elementField;
  switch (type) {
    case 'boolean':
      elementField = (
        <input
          type="checkbox"
          checked={value}
          name={name}
          onChange={() => {
            pipelineElementPropertyUpdated(pipelineId, elementId, name, 'boolean', !value);
          }}
        />
      );
      break;
    case 'int':
      elementField = (
        <input
          type="number"
          name={name}
          value={parseInt(value, 10)}
          onChange={({ target: { value } }) => {
            pipelineElementPropertyUpdated(
              pipelineId,
              elementId,
              name,
              'integer',
              parseInt(value, 10),
            );
          }}
        />
      );
      break;
    case 'docref':
      elementField = (
        <AppSearchBar
          pickerId={`${name}_docRefModalPicker`}
          typeFilters={docRefTypes}
          value={value}
          onChange={(node) => {
            pipelineElementPropertyUpdated(pipelineId, elementId, name, 'docref', node);
          }}
        />
      );

      break;

    case 'string':
      elementField = (
        <input
          value={value}
          name={name}
          onChange={({ target: { value } }) => {
            pipelineElementPropertyUpdated(pipelineId, elementId, name, type, value);
          }}
        />
      );
      break;
    case 'pipelinereference':
      elementField = <div>TODO</div>;
      break;
    default:
      elementField = (
        <input
          value={value}
          name={name}
          onChange={({ target: { value } }) => {
            pipelineElementPropertyUpdated(pipelineId, elementId, name, type, value);
          }}
        />
      );
      break;
  }
  return elementField;
};


ElementPropertyField.propTypes = {
  pipelineElementPropertyUpdated: PropTypes.object.isRequired,
  value: PropTypes.string.isRequired,
  name: PropTypes.string.isRequired,
  pipelineId: PropTypes.string.isRequired,
  elementId: PropTypes.string.isRequired,
  type: PropTypes.string.isRequired,
  docRefTypes: PropTypes.array.isRequired,
}

export default ElementPropertyField;