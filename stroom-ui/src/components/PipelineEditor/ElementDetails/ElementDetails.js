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
import { Image, Form } from 'semantic-ui-react';
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
    // These next few lines involve extracting the relevant properties from the pipeline.
    // The types of the properties and their values are in different places.
    const element = pipeline.merged.elements.add.find(element => element.id === selectedElementId);
    const elementType = elements.elements.find(e => e.type === element.type);
    const elementTypeProperties = elements.elementProperties[element.type];
    const sortedElementTypeProperties = Object.values(elementTypeProperties).sort((a, b) => a.displayPriority > b.displayPriority);

    return {
      element,
      elementType,
      elementTypeProperties: sortedElementTypeProperties,
      selectedElementId
    };
  }),
);

const ElementDetails = ({
  pipelineId,
  pipelineState: { pipeline },
  onClose,
  element,
  elementType,
  elementTypeProperties,
  elementProperties,
  elementPropertiesInChild,
  selectedElementId,
}) => {
  const title = (
    <div className="element-details__title">
      <Image
        size="small"
        src={require(`../images/${elementType.icon}`)}
        className="element-details__icon"
      />
      <div>
        <strong>{element.id}</strong>
      </div>
    </div>
  );

  const content = (
    <React.Fragment>
      <p>
        This element is a <strong>{element.type}</strong>.
      </p>
      <Form className="element-details__form">
        {Object.keys(elementTypeProperties).length === 0 ? (
          <p>There is nothing to configure for this element </p>
        ) : (
            elementTypeProperties.map((elementTypeProperty) => (
              <ElementProperty
                pipelineId={pipelineId}
                elementId={element.id}
                key={elementTypeProperty.name}
                name={elementTypeProperty.name}
                type={elementTypeProperty.type}
                elementTypeProperty={elementTypeProperty}
                description={elementTypeProperty.description}
                selectedElementId={selectedElementId}
              />
            )
            )
          )}
      </Form>
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
