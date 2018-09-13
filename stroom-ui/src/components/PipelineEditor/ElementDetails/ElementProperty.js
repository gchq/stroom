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
import { getParentProperty, getChildValue, getElementValue } from '../pipelineUtils';
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
    pipelineElementPropertyRevertToParent,
    pipelineElementPropertyRevertToDefault,
    pipelineState: { pipeline },
    elementId,
    pipelineId,
    elementType }) => {

    const value = getElementValue(pipeline, elementId, elementType.name)
    const childValue = getChildValue(pipeline, elementId, elementType.name);
    const parentValue = getParentProperty(
      pipeline.configStack,
      elementId,
      elementType.name,
    );

    const details = getDetails({
      value,
      parentValue,
      defaultValue: elementType.defaultValue,
      type: elementType.type,
      pipelineElementPropertyRevertToParent,
      pipelineElementPropertyRevertToDefault,
      elementId,
      name: elementType.name,
      pipelineId,
      childValue,
    });

    return {
      type: elementType.type.toLowerCase(),
      value: details.actualValue,
      inheritanceAdvice: details.info,
      docRefTypes: elementType.docRefTypes,
      name: elementType.name,
      description: elementType.description,
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
  value,
  inheritanceAdvice,
}) => (
    <React.Fragment>
      <label>{description}</label>
      <ElementPropertyField
        {...{
          pipelineElementPropertyUpdated,
          value,
          name,
          pipelineId,
          elementId,
          type,
          docRefTypes,
        }}
      />
      <div className="element-property__advice">
        <p>
          The <em>field name</em> of this property is <strong>{name}</strong>
        </p>
        {inheritanceAdvice}
      </div>
    </React.Fragment>
  );

ElementProperty.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  elementId: PropTypes.string.isRequired,
  elementType: PropTypes.object.isRequired,
};

export default enhance(ElementProperty);
