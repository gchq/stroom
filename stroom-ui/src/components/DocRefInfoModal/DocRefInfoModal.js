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
import { compose, branch, renderNothing, renderComponent, withProps } from 'recompose';

import { Header, Button, Form, Loader } from 'semantic-ui-react';

import ThemedModal from 'components/ThemedModal';
import { actionCreators } from './redux';

const { docRefInfoClosed } = actionCreators;

const enhance = compose(
  connect(
    ({ docRefInfo: { isOpen, docRefInfo } }, props) => ({
      isOpen,
      docRefInfo,
    }),
    { docRefInfoClosed },
  ),
  branch(({ isOpen }) => !isOpen, renderNothing),
  branch(
    ({ docRefInfo }) => !docRefInfo,
    renderComponent(() => <Loader active>Awaiting DocRef Info </Loader>),
  ),
  withProps(({ docRefInfo: { createTime, updateTime } }) => ({
    formattedCreateTime: new Date(createTime).toLocaleString('en-GB', { timeZone: 'UTC' }),
    formattedUpdateTime: new Date(updateTime).toLocaleString('en-GB', { timeZone: 'UTC' }),
  })),
);

const DocRefInfoModal = ({
  isOpen,
  docRefInfo,
  docRefInfoClosed,
  formattedCreateTime,
  formattedUpdateTime,
}) => (
  <ThemedModal
    open={isOpen}
    onClose={docRefInfoClosed}
    header={<Header className="header" icon="info" content="Document Information" />}
    content={
      <Form>
        <Form.Group widths="equal">
          <Form.Input label="Type" type="text" value={docRefInfo.docRef.type} />
          <Form.Input label="UUID" type="text" value={docRefInfo.docRef.uuid} />
          <Form.Input label="Name" type="text" value={docRefInfo.docRef.name} />
        </Form.Group>
        <Form.Group widths="equal">
          <Form.Input label="Created by" type="text" value={docRefInfo.createUser} />
          <Form.Input label="at" type="text" value={formattedCreateTime} />
        </Form.Group>
        <Form.Group widths="equal">
          <Form.Input label="Updated by" type="text" value={docRefInfo.updateUser} />
          <Form.Input label="at" type="text" value={formattedUpdateTime} />
        </Form.Group>
        <Form.Input label="Other Info" type="text" value={docRefInfo.otherInfo} />
      </Form>
    }
    actions={
      <Button negative onClick={docRefInfoClosed}>
        Close
      </Button>
    }
  />
);

export default enhance(DocRefInfoModal);
