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

import { compose } from 'redux';
import { connect } from 'react-redux';
import { Field, reduxForm } from 'redux-form';

import { Form, Button } from 'semantic-ui-react';

import { restartAddPipelineElement } from './redux';

const NameNewElement = ({
  addElementToPipelineWizard,
  restartAddPipelineElement,
  handleSubmit,
}) => (
  <Form>
    <Button onClick={restartAddPipelineElement}>Back</Button>
    <Form.Field>
      <label>Name</label>
      <Field name="name" component="input" type="text" placeholder="Name" />
    </Form.Field>
    <Button onClick={handleSubmit}>Submit</Button>
  </Form>
);

NameNewElement.propTypes = {
  // State
  addElementToPipelineWizard: PropTypes.object.isRequired,
  // Actions
  restartAddPipelineElement: PropTypes.func.isRequired,
  // Redux form
  handleSubmit: PropTypes.func.isRequired,
};

export default compose(
  connect(
    state => ({
      addElementToPipelineWizard: state.addElementToPipelineWizard,
    }),
    {
      restartAddPipelineElement,
    },
  ),
  reduxForm({ form: 'addElementToPipeline' }),
)(NameNewElement);
