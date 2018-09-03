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
import { compose } from 'recompose';
import { connect } from 'react-redux';

import { storiesOf, addDecorator } from '@storybook/react';
import StoryRouter from 'storybook-react-router';
import { Field, reduxForm } from 'redux-form';
import { Form } from 'semantic-ui-react';

import DocTypeFilters from './DocTypeFilters';
import DocRefTypePicker from './DocRefTypePicker';
import { actionCreators } from './redux';
import { testDocRefsTypes } from './test';

import { ReduxDecorator } from 'lib/storybook/ReduxDecorator';
import { PollyDecorator } from 'lib/storybook/PollyDecorator';

import 'styles/main.css';
import 'semantic/dist/semantic.min.css';

const enhance = compose(
  connect(
    ({ form }) => ({
      thisForm: form.docTypeFilterTest,
      initialValues: {
        docTypes: [],
      },
    }),
    {},
  ),
  reduxForm({
    form: 'docTypeFilterTest',
  }),
);

let TestForm = ({ thisForm }) => (
  <Form>
    <Form.Field>
      <label>Chosen Doc Type</label>
      <Field
        name="docType"
        component={({ input: { onChange, value } }) => (
          <DocRefTypePicker onChange={onChange} value={value} />
        )}
      />
    </Form.Field>
    <Form.Field>
      <label>Chosen Doc Types</label>
      <Field
        name="docTypes"
        component={({ input: { onChange, value } }) => (
          <DocTypeFilters onChange={onChange} value={value} />
        )}
      />
    </Form.Field>
    {thisForm &&
      thisForm.values && (
        <div>
          <div>Doc Type: {thisForm.values.docType}</div>
          <div>Doc Types: {thisForm.values.docTypes.join(',')}</div>
        </div>
      )}
  </Form>
);

TestForm = enhance(TestForm);

storiesOf('Doc Type Filters', module)
  .addDecorator(PollyDecorator({ docRefTypes: testDocRefsTypes }))
  .addDecorator(ReduxDecorator)
  .add('Doc Type Filter', () => <TestForm />);
