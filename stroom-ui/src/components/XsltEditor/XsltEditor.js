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

// eslint-disable-next-line
import brace from 'brace';
import 'brace/mode/xml';
import 'brace/theme/github';
import 'brace/keybinding/vim';

import { compose, lifecycle, renderComponent, branch } from 'recompose';
import { withRouter } from 'react-router-dom';
import { connect } from 'react-redux';
import AceEditor from 'react-ace';
import { Loader, Header } from 'semantic-ui-react';

import SaveXslt from './SaveXslt';
import DocRefBreadcrumb from 'components/DocRefBreadcrumb';
import WithHeader from 'components/WithHeader';
import { fetchXslt } from './xsltResourceClient';
import { saveXslt } from './xsltResourceClient';
import { openDocRef } from 'sections/RecentItems';

import { actionCreators } from './redux';

const { xsltUpdated } = actionCreators;

const enhance = compose(
  withRouter,
  connect(
    ({ xslt }, { xsltId }) => ({
      xslt: xslt[xsltId],
    }),
    { fetchXslt, xsltUpdated, saveXslt, openDocRef },
  ),
  lifecycle({
    componentDidMount() {
      const { fetchXslt, xsltId } = this.props;

      fetchXslt(xsltId);
    },
  }),
  branch(({ xslt }) => !xslt, renderComponent(() => <Loader active>Loading XSLT</Loader>)),
);

const XsltEditor = ({ xsltId, xslt, xsltUpdated, saveXslt, openDocRef, history }) => (
  <WithHeader
    docRefUuid={xsltId}
    header={
      <Header as="h3">
        <img
          className="stroom-icon--large"
          alt="X"
          src={require('../../images/docRefTypes/XSLT.svg')}
        />
        <Header.Content>
          {xsltId}
        </Header.Content>
        <Header.Subheader><DocRefBreadcrumb docRefUuid={xsltId} openDocRef={l => openDocRef(history, l)} /></Header.Subheader>
      </Header>
    }
    content={<div className="xslt-editor">
      <div className="xslt-editor__ace-container">
        <AceEditor
          style={{ width: '100%', height: '100%', minHeight: '25rem' }}
          name={`${xsltId}-ace-editor`}
          mode="xml"
          theme="github"
          keyboardHandler="vim"
          value={xslt.xsltData}
          onChange={(newValue) => {
            if (newValue !== xslt.xsltData) xsltUpdated(xsltId, newValue);
          }}
        />
      </div>
    </div>}
    actionBarItems={
      <React.Fragment>
        <SaveXslt saveXslt={saveXslt} xsltId={xsltId} xslt={xslt} />
      </React.Fragment>
    }
  />
);

XsltEditor.propTypes = {
  xsltId: PropTypes.string.isRequired,
};

export default enhance(XsltEditor);
