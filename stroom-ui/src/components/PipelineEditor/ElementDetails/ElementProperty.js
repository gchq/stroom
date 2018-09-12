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
import { compose, withProps } from 'recompose';
import { connect } from 'react-redux';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome'

import { actionCreators } from '../redux';
import { getParentProperty } from '../pipelineUtils';
import { getDetails } from './elementDetailsUtils';
import ElementPropertyField from './ElementPropertyField';
import Tooltip from 'components/Tooltip';

const {
  pipelineElementPropertyUpdated,
  pipelineElementPropertyRevertToParent,
  pipelineElementPropertyRevertToDefault,
} = actionCreators;

const enhance = compose(connect(
  ({ pipelineEditor: { pipelineStates, elements } }, { pipelineId }) => {
    const pipelineState = pipelineStates[pipelineId];
    return {
      pipelineState,
    };
  },
  {
    pipelineElementPropertyUpdated,
    pipelineElementPropertyRevertToParent,
    pipelineElementPropertyRevertToDefault,
  },
),
  withProps(({
    defaultValue,
    type,
    pipelineElementPropertyRevertToParent,
    pipelineElementPropertyRevertToDefault,
    pipelineState: { pipeline },
    elementId,
    name,
    pipelineId,
    elementTypeProperty,
    selectedElementId }) => {

    const elementProperties = pipeline.merged.properties.add.filter(property => property.element === selectedElementId);

    const elementPropertiesInChild = pipeline.configStack[
      pipeline.configStack.length - 1
    ].properties.add.filter(property => property.element === selectedElementId);

    const docRefTypes = elementTypeProperty.docRefTypes
      ? elementTypeProperty.docRefTypes
      : undefined;

    const parentValue = getParentProperty(
      pipeline.configStack,
      elementId,
      elementTypeProperty.name,
    );

    const value = elementProperties.find(element => element.name === elementTypeProperty.name);
    const childValue = elementPropertiesInChild.find(element => element.name === elementTypeProperty.name);

    return {
      type: type.toLowerCase(),
      details: getDetails({
        value,
        parentValue,
        defaultValue: elementTypeProperty.defaultValue,
        type,
        pipelineElementPropertyRevertToParent,
        pipelineElementPropertyRevertToDefault,
        elementId,
        name,
        pipelineId,
        childValue,
      }),
      docRefTypes,
    };
  }),
);

const ElementProperty = ({
  name,
  description,
  type,
  docRefTypes,
  pipelineId,
  pipelineElementPropertyUpdated,
  elementId,
  details,
}) => (
    <div>
      <div className="element-details__field">
        <label>{description}</label>
        <ElementPropertyField
          {...{
            pipelineElementPropertyUpdated,
            value: details.actualValue,
            name,
            pipelineId,
            elementId,
            type,
            docRefTypes,
          }}
        />
      </div>
      <Tooltip
        hoverable
        trigger={<FontAwesomeIcon icon="cog" color="blue" size="lg" />}
        content={
          <div>
            <p>
              The <em>field name</em> of this property is <strong>{name}</strong>
            </p>
            {details.info}
          </div>
        }
      />
    </div>
  );

ElementProperty.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  name: PropTypes.string.isRequired,
  elementId: PropTypes.string.isRequired,
  description: PropTypes.string.isRequired,
  type: PropTypes.string.isRequired,
  pipelineElementPropertyUpdated: PropTypes.func.isRequired,
  elementTypeProperty: PropTypes.any,
  elementProperties: PropTypes.any,
  selectedElementId: PropTypes.any,
};

export default enhance(ElementProperty);
