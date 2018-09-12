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
import { getDetails } from './elementDetailsUtils';
import ElementPropertyField from './ElementPropertyField';
import Tooltip from 'components/Tooltip';

const {
  pipelineElementPropertyUpdated,
  pipelineElementPropertyRevertToParent,
  pipelineElementPropertyRevertToDefault,
} = actionCreators;

const enhance = compose(connect(
  (state, props) => ({
    // state
  }),
  {
    pipelineElementPropertyUpdated,
    pipelineElementPropertyRevertToParent,
    pipelineElementPropertyRevertToDefault,
  },
),
  withProps(({ value,
    parentValue,
    defaultValue,
    type,
    pipelineElementPropertyRevertToParent,
    pipelineElementPropertyRevertToDefault,
    elementId,
    name,
    pipelineId,
    childValue, }) => {
    return {
      type: type.toLowerCase(),
      details: getDetails({
        value,
        parentValue,
        defaultValue,
        type,
        pipelineElementPropertyRevertToParent,
        pipelineElementPropertyRevertToDefault,
        elementId,
        name,
        pipelineId,
        childValue,
      }),
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
  docRefTypes: PropTypes.array,
  defaultValue: PropTypes.string.isRequired,
  value: PropTypes.object,
  childValue: PropTypes.object,
  parentValue: PropTypes.object,
  pipelineElementPropertyUpdated: PropTypes.func.isRequired,
};

export default enhance(ElementProperty);
