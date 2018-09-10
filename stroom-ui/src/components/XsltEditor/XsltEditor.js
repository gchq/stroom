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

import { compose, lifecycle, renderComponent, branch, withHandlers } from 'recompose';
import { withRouter } from 'react-router-dom';
import { connect } from 'react-redux';
import { Loader, Header, Grid, Button } from 'semantic-ui-react';

import Tooltip from 'components/Tooltip';

import DocRefImage from 'components/DocRefImage';
import DocRefBreadcrumb from 'components/DocRefBreadcrumb';
import { fetchXslt } from './xsltResourceClient';
import { saveXslt } from './xsltResourceClient';
import ThemedAceEditor from 'components/ThemedAceEditor';

import { actionCreators } from './redux';

const { xsltUpdated } = actionCreators;

const enhance = compose(
  withRouter,
  withHandlers({
    openDocRef: ({ history }) => d => history.push(`/s/doc/${d.type}/${d.uuid}`),
  }),
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
  branch(({ xsltState }) => !xsltState, renderComponent(() => <Loader active>Loading XSLT</Loader>)),
);

const XsltEditor = ({
  xsltUuid,
  xsltState: { isDirty, isSaving, xsltData },
  xsltUpdated,
  saveXslt,
  openDocRef,
  history,
}) => (
  <React.Fragment>
    <Grid className="content-tabs__grid">
      <Grid.Column width={12}>
        <Header as="h3">
          <DocRefImage docRefType="XSLT" />
          <Header.Content>{xsltUuid}</Header.Content>
          <Header.Subheader>
            <DocRefBreadcrumb docRefUuid={xsltUuid} openDocRef={openDocRef} />
          </Header.Subheader>
        </Header>
      </Grid.Column>
      <Grid.Column width={4}>
        <Tooltip
          trigger={
            <Button
              floated="right"
              circular
              icon="save"
              color={isDirty ? 'blue' : undefined}
              loading={isSaving}
              onClick={() => {
                if (xsltUuid) saveXslt(xsltUuid);
              }}
            />
          }
          content={isDirty ? 'Save changes' : 'Changes saved'}
        />
      </Grid.Column>
    </Grid>
    <div className="xslt-editor">
      <div className="xslt-editor__ace-container">
        <ThemedAceEditor
          style={{ width: '100%', height: '100%', minHeight: '25rem' }}
          name={`${xsltUuid}-ace-editor`}
          mode="xml"
          value={xsltData}
          onChange={(newValue) => {
            if (newValue !== xsltData) xsltUpdated(xsltUuid, newValue);
          }}
        />
      </div>
    </div>
  </React.Fragment>
);

XsltEditor.propTypes = {
  xsltUuid: PropTypes.string.isRequired,
};

export default enhance(XsltEditor);
