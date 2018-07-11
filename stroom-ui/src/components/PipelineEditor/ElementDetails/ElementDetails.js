import React from 'react';
import PropTypes from 'prop-types';

import { compose, branch, renderComponent } from 'recompose';

import { connect } from 'react-redux';

import { Container, Message, Image, Form } from 'semantic-ui-react';

import { reduxForm } from 'redux-form';

import HorizontalPanel from 'prototypes/HorizontalPanel';

import ElementField from './ElementField';

const enhance = compose(
  connect(
    (state, props) => {
      const pipeline = state.pipelineEditor.pipelines[props.pipelineId];
      let initialValues;
      let selectedElementId;
      if (pipeline) {
        initialValues = pipeline.selectedElementInitialValues;
        selectedElementId = pipeline.selectedElementId;
      }
      const form = `${props.pipelineId}-elementDetails`;

      return {
        // for our component
        elements: state.pipelineEditor.elements,
        selectedElementId,
        pipeline,
        // for redux-form
        form,
        initialValues,
      };
    },
    {
      // actions
    },
  ),
  reduxForm(),
  branch(
    ({ selectedElementId }) => !selectedElementId,
    renderComponent(() => (
      <Container className="element-details">
        <Message>
          <Message.Header>Please select an element</Message.Header>
        </Message>
      </Container>
    )),
  ),
);

const ElementDetails = ({
  pipelineId, pipeline, selectedElementId, elements, onClose,
}) => {
  const config = pipeline.pipeline.configStack[pipeline.pipeline.configStack.length - 1];
  const element = config.elements.add.find(element => element.id === selectedElementId);
  // TODO:  this doesn't work when elements are spread out over a config stack
  // const element = pipeline.pipeline.merged.elements.add.find(element => element.id === selectedElementId);
  const elementProperties = config.properties.add.filter(property => property.element === selectedElementId);
  const elementType = elements.elements.find(e => e.type === element.type);
  const elementTypeProperties = elements.elementProperties[element.type];

  const sortedElementTypeProperties = Object.values(elementTypeProperties).sort((a, b) => a.displayPriority > b.displayPriority);

  const title = (
    <React.Fragment>
      <Image
        size="small"
        src={require(`../images/${elementType.icon}`)}
        className="element-details__icon"
      />
      {element.id}
    </React.Fragment>
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
          sortedElementTypeProperties.map((elementTypeProperty) => {
            const docRefTypes = elementTypeProperty.docRefTypes
              ? elementTypeProperty.docRefTypes
              : undefined;
            const defaultValue = parseInt(elementTypeProperty.defaultValue, 10);
            const property = elementProperties.find(element => element.name === elementTypeProperty.name);
            return (
              <ElementField
                pipelineId={pipelineId}
                elementId={element.id}
                key={elementTypeProperty.name}
                name={elementTypeProperty.name}
                type={elementTypeProperty.type}
                docRefTypes={docRefTypes}
                description={elementTypeProperty.description}
                defaultValue={defaultValue}
                value={property}
              />
            );
          })
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
  // Set by owner
  pipelineId: PropTypes.string.isRequired,
  onClose: PropTypes.func,
};

export default enhance(ElementDetails);
