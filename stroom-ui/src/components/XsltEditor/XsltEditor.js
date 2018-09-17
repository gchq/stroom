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
import { compose, lifecycle, renderComponent, branch, withHandlers, withProps } from 'recompose';
import { connect } from 'react-redux';

import DocRefEditor from 'components/DocRefEditor';
import Loader from 'components/Loader';
import { fetchXslt, saveXslt } from './xsltResourceClient';
import ThemedAceEditor from 'components/ThemedAceEditor';
import { actionCreators } from './redux';

const { xsltUpdated } = actionCreators;

const enhance = compose(
  connect(
    ({ xsltEditor }, { xsltUuid }) => ({
      xsltState: xsltEditor[xsltUuid],
    }),
    {
      fetchXslt,
      xsltUpdated,
      saveXslt,
    },
  ),
  lifecycle({
    componentDidMount() {
      const { fetchXslt, xsltUuid } = this.props;

      fetchXslt(xsltUuid);
    },
  }),
  branch(
    ({ xsltState }) => !xsltState,
    renderComponent(() => <Loader message="Loading XSLT..." />),
  ),
  withHandlers({
    onContentChange: ({ xsltUpdated, xsltUuid, xsltState: { xsltData } }) => (newValue) => {
      if (newValue !== xsltData) xsltUpdated(xsltUuid, newValue);
    },
    onClickSave: ({ saveXslt, xsltUuid }) => e => saveXslt(xsltUuid),
  }),
  withProps(({ xsltState: { isDirty, isSaving }, onClickSave }) => ({
    actionBarItems: [
      {
        icon: 'save',
        disabled: !(isDirty || isSaving),
        title: isSaving ? 'Saving...' : isDirty ? 'Save' : 'Saved',
        onClick: onClickSave,
      },
    ],
  })),
);

const XsltEditor = ({
  xsltUuid, xsltState: { xsltData }, onContentChange, actionBarItems,
}) => (
  <DocRefEditor
    docRef={{
      type: 'XSLT',
      uuid: xsltUuid,
    }}
    actionBarItems={actionBarItems}
  >
    <ThemedAceEditor
      style={{ width: '100%', height: '100%', minHeight: '25rem' }}
      name={`${xsltUuid}-ace-editor`}
      mode="xml"
      value={xsltData}
      onChange={onContentChange}
    />
  </DocRefEditor>
);

XsltEditor.propTypes = {
  xsltUuid: PropTypes.string.isRequired,
};

export default enhance(XsltEditor);
