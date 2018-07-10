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

import { connect } from 'react-redux';
import { compose, branch, lifecycle, renderNothing, renderComponent } from 'recompose';

import { Modal, Button, Form, Loader } from 'semantic-ui-react';

import { actionCreators } from './redux';

const { docRefInfoClosed } = actionCreators;

const enhance = compose(
  connect(
    (state, props) => ({
      isOpen: state.docExplorer.docRefInfo.isOpen,
      docRefInfo: state.docExplorer.docRefInfo.docRefInfo,
    }),
    { docRefInfoClosed },
  ),
  branch(({ isOpen }) => !isOpen, renderNothing),
  branch(
    ({ docRefInfo }) => !docRefInfo,
    renderComponent(() => <Loader active>Awaiting DocRef Info </Loader>),
  ),
);

const DocRefInfoModal = ({ isOpen, docRefInfo, docRefInfoClosed }) => (
  <Modal open={isOpen} onClose={docRefInfoClosed} size="small" dimmer="inverted">
    {' '}
    <Modal.Header>Doc Ref Info</Modal.Header>
    <Modal.Content scrolling>
      <Form>
        <Form.Group widths="equal">
          <Form.Input label="Type" type="text" value={docRefInfo.docRef.type} />
          <Form.Input label="UUID" type="text" value={docRefInfo.docRef.uuid} />
          <Form.Input label="Name" type="text" value={docRefInfo.docRef.name} />
        </Form.Group>
        <Form.Group widths="equal">
          <Form.Input label="Created by" type="text" value={docRefInfo.createUser} />
          <Form.Input
            label="at"
            type="text"
            value={new Date(docRefInfo.createTime).toLocaleString('en-GB', { timeZone: 'UTC' })}
          />
        </Form.Group>
        <Form.Group widths="equal">
          <Form.Input label="Updated by" type="text" value={docRefInfo.updateUser} />
          <Form.Input
            label="at"
            type="text"
            value={new Date(docRefInfo.updateTime).toLocaleString('en-GB', { timeZone: 'UTC' })}
          />
        </Form.Group>
        <Form.Input label="Other Info" type="text" value={docRefInfo.otherInfo} />
      </Form>
    </Modal.Content>
    <Modal.Actions>
      <Button negative onClick={docRefInfoClosed}>
        Close
      </Button>
    </Modal.Actions>
  </Modal>
);

export default enhance(DocRefInfoModal);
