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
import { compose, branch, renderComponent, withProps } from 'recompose';
import { connect } from 'react-redux';
import { reduxForm } from 'redux-form';

import HorizontalPanel from 'components/HorizontalPanel';
import ElementProperty from './ElementProperty';

const enhance = compose(
  connect(
    ({ pipelineEditor: { pipelineStates, elements } }, { pipelineId }) => {
      const pipelineState = pipelineStates[pipelineId];
      let initialValues;
      let selectedElementId;
      if (pipelineState) {
        initialValues = pipelineState.selectedElementInitialValues;
        selectedElementId = pipelineState.selectedElementId;
      }
      const form = `${pipelineId}-elementDetails`;

      return {
        elements,
        selectedElementId,
        pipelineState,
        form,
        initialValues,
      };
    },
    {},
  ),
  reduxForm(),
  branch(
    ({ selectedElementId }) => !selectedElementId,
    renderComponent(() => (
      <div className="element-details__nothing-selected">
        <h3>Please select an element</h3>
      </div>
    )),
  ),
  withProps(({ pipelineState: { pipeline }, elements, selectedElementId }) => {
    const elementType = pipeline.merged.elements.add.find(element => element.id === selectedElementId).type;
    const elementTypeProperties = elements.elementProperties[elementType];
    const sortedElementTypeProperties = Object.values(elementTypeProperties).sort((a, b) => a.displayPriority > b.displayPriority);

    return {
      icon: elements.elements.find(e => e.type === elementType).icon,
      typeName: elementType,
      elementTypeProperties: sortedElementTypeProperties,
      selectedElementId
    };
  }),
);

const ElementDetails = ({
  pipelineId,
  onClose,
  icon,
  elementTypeProperties,
  selectedElementId,
  typeName,
}) => {
  const title = (
    <div className="element-details__title">
      <img
        src={require(`../images/${icon}`)}
        className="element-details__icon"
      />
      <div>
        <h3>{selectedElementId}</h3>
      </div>
    </div>
  );

  const content = (
    <React.Fragment>
      <p className="element-details__summary">
        This element is a <strong>{typeName}</strong>.
      </p>
      <form className="element-details__form">
        {Object.keys(elementTypeProperties).length === 0 ? (
          <p>There is nothing to configure for this element </p>
        ) : (
            elementTypeProperties.map((elementType) => (
              <ElementProperty
                pipelineId={pipelineId}
                elementId={selectedElementId}
                key={elementType.name}
                elementType={elementType}
              />
            ))
          )}
      </form>
    </React.Fragment>
  );

  return (
    <HorizontalPanel
      className="element-details__panel"
      title={title}
      onClose={() => onClose()}
      content={content}
      titleColumns={6}
      menuColumns={10}
      headerSize="h3"
    />
  );
};

ElementDetails.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  onClose: PropTypes.func,
};

export default enhance(ElementDetails);
