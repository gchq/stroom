import React from 'react';
import PropTypes from 'prop-types';

import { compose } from 'recompose';
import { connect } from 'react-redux';

import { Container, Header, Message, Image, Grid, Form } from 'semantic-ui-react';

import { reduxForm } from 'redux-form';

import ElementField from './ElementField';

const ElementDetails = ({
  pipelineId, pipeline, selectedElementId, elements,
}) => {
  if (!selectedElementId) {
    return (
      <Container className="element-details">
        <Message>
          <Message.Header>Please select an element</Message.Header>
        </Message>
      </Container>
    );
  }

  const element = pipeline.pipeline.elements.add.find(element => element.id === selectedElementId);
  const elementProperties = pipeline.pipeline.properties.add.filter(property => property.element === selectedElementId);
  const elementType = elements.elements[element.type];
  const elementTypeProperties = elements.elementProperties[element.type];

  return (
    <Container className="element-details">
      <Grid>
        <Grid.Row width="16">
          <Header as="h2" className="element-details__header">
            <Image
              src={require(`../images/${elementType.icon}`)}
              className="element-details__icon"
            />
            {element.id}
          </Header>
        </Grid.Row>
        <Grid.Row>
          <Header as="h4"> This is a {elementType.type} element</Header>
        </Grid.Row>
        <Form className="element-details__form">
          {Object.keys(elementTypeProperties).map(key => (
            <ElementField
              key={key}
              name={key}
              type={elementTypeProperties[key].type}
              description={elementTypeProperties[key].description}
              defaultValue={elementTypeProperties[key].defaultValue}
              value={elementProperties.find(element => element.name === key)}
            />
          ))}
        </Form>
      </Grid>
    </Container>
  );
};

ElementDetails.propTypes = {
  // Set by owner
  pipelineId: PropTypes.string.isRequired,

  // Redux state
  pipeline: PropTypes.object.isRequired,
  selectedElementId: PropTypes.string,
  elements: PropTypes.object.isRequired,
};

export default compose(
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
)(ElementDetails);
