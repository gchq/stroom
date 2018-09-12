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
import { reduxForm, Field } from 'redux-form';
import { Header, Form } from 'semantic-ui-react';
import { InputField } from 'react-semantic-redux-form';

import ThemedModal from 'components/ThemedModal';
import DialogActionButtons from './DialogActionButtons';
import { required, minLength2 } from 'lib/reduxFormUtils';
import { actionCreators } from './redux';
import { DocRefTypePicker } from 'components/DocRefTypes';
import explorerClient from 'components/FolderExplorer/explorerClient';
import PermissionInheritancePicker from 'components/PermissionInheritancePicker';

const { createDocument } = explorerClient;

const { completeDocRefCreation } = actionCreators;

const enhance = compose(
  connect(
    ({
      userSettings: { theme },
      folderExplorer: {
        newDoc: { isOpen, destination },
      },
      form,
    }) => ({
      theme,
      isOpen,
      destination,
      newDocRefForm: form.newDocRef,
    }),
    { completeDocRefCreation, createDocument },
  ),
  reduxForm({
    form: 'newDocRef',
    enableReinitialize: true,
    touchOnChange: true,
  }),
);

const NewDocDialog = ({
  isOpen,
  stage,
  completeDocRefCreation,
  createDocument,
  newDocRefForm,
  destination,
  // We need to include the theme because modals are mounted outside the root
  // div, i.e. outside the div which contains the theme class.
  theme,
}) => (
    <ThemedModal
      isOpen={isOpen}
      onClose={completeDocRefCreation}
      size="small"
      closeOnDimmerClick={false}
      header={
        <Header
          className="header"
          icon="plus"
          content={`Create a New Doc Ref in ${destination && destination.name}`}
        />
      }
      content={
        <Form>
          <Form.Field>
            <label>Doc Ref Type</label>
            <Field
              name="docRefType"
              component={({ input: { onChange, value } }) => (
                <DocRefTypePicker pickerId="new-doc-ref-type" onChange={onChange} value={value} />
              )}
            />
          </Form.Field>
          <Form.Field>
            <label>Name</label>
            <Field
              name="docRefName"
              component={InputField}
              type="text"
              placeholder="Name"
              validate={[required, minLength2]}
            />
          </Form.Field>
          <Form.Field>
            <label>Permission Inheritance</label>
            <Field
              className="raised-border"
              name="permissionInheritance"
              component={({ input: { onChange, value } }) => (
                <PermissionInheritancePicker onChange={onChange} value={value} />
              )}
            />
          </Form.Field>
        </Form>
      }
      actions={
        <DialogActionButtons
          onCancel={completeDocRefCreation}
          onChoose={() => createDocument(
            newDocRefForm.values.docRefType,
            newDocRefForm.values.docRefName,
            destination,
            newDocRefForm.values.permissionInheritance,
          )}
        />
      }
    />
  );

export default enhance(NewDocDialog);
