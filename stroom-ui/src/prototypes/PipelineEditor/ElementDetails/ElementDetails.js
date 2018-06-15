import React from 'react';
import PropTypes from 'prop-types';

import { compose } from 'recompose';
import { connect } from 'react-redux';

import { Container, Header, Message, Image, Grid, Form } from 'semantic-ui-react';

import { reduxForm } from 'redux-form';

import { withPipeline } from '../withPipeline';

import ElementField from './ElementField';

const ElementDetails = ({
  pipelineId, pipeline, selectedElementId, elements,
}) => {
  let element,
    elementProperties,
    elementType,
    elementTypeProperties;

  if (selectedElementId) {
    element = pipeline.elements.add.find(element => element.id === selectedElementId);
    elementProperties = pipeline.properties.add.filter(property => property.element === selectedElementId);
    elementType = elements.elements[element.type];
    elementTypeProperties = elements.elementProperties[element.type];
    console.log(elementType);
  }

  return (
    <Container className="element-details">
      {selectedElementId ? (
        <div>
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
              {Object.keys(elementTypeProperties).map((key) => {
                const thingy = elementProperties.find(element => element.name === key);
                return (
                  <ElementField
                    name={key}
                    type={elementTypeProperties[key].type}
                    description={elementTypeProperties[key].description}
                    defaultValue={elementTypeProperties[key].defaultValue}
                    value={elementProperties.find(element => element.name === key)}
                  />
                );
              })}
            </Form>
          </Grid>
        </div>
      ) : (
        <Message>
          <Message.Header>Please select an element</Message.Header>
        </Message>
      )}
    </Container>
  );
};

ElementDetails.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  pipeline: PropTypes.object.isRequired,
};

export default compose(
  connect(
    state => ({
      elements: state.elements,
      // state
    }),
    {
      // actions
    },
  ),
  reduxForm({ form: 'elementDetails' }),
  withPipeline(),
)(ElementDetails);
