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
      const pipeline = state.pipelines[props.pipelineId];
      let initialValues;
      let selectedElementId;
      if (pipeline) {
        initialValues = pipeline.selectedElementInitialValues;
        selectedElementId = pipeline.selectedElementId;
      }
      const form = `${props.pipelineId}-elementDetails`;

      return {
        // for our component
        elements: state.elements,
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

const ElementDetails = enhance(({
  pipelineId, pipeline, selectedElementId, elements, onClose,
}) => {
  const element = pipeline.pipeline.merged.elements.add.find(element => element.id === selectedElementId);
  const elementProperties = pipeline.pipeline.merged.properties.add.filter(property => property.element === selectedElementId);
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
          sortedElementTypeProperties.map(elementTypeProperty => (
            <ElementField
              key={elementTypeProperty.name}
              name={elementTypeProperty.name}
              type={elementTypeProperty.type}
              description={elementTypeProperty.description}
              defaultValue={parseInt(elementTypeProperty.defaultValue, 10)}
              value={elementProperties.find(element => element.name === elementTypeProperty.name)}
            />
          ))
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
});

ElementDetails.propTypes = {
  // Set by owner
  pipelineId: PropTypes.string.isRequired,
  onClose: PropTypes.func,
};

export default ElementDetails;
