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
import brace from 'brace';

import 'brace/mode/xml';
import 'brace/theme/github';

import { compose, lifecycle, renderComponent, branch } from 'recompose';
import { connect } from 'react-redux';
import AceEditor from 'react-ace';
import { Loader } from 'semantic-ui-react';

import SaveXslt from './SaveXslt';
import { fetchXslt, saveXslt } from './xsltResourceClient';
import { withConfig } from 'startup/config';

import { actionCreators } from './redux';

const { xsltUpdated } = actionCreators;

const enhance = compose(
  withConfig,
  connect(
    (state, props) => ({
      xslt: state.xslt[props.xsltId],
    }),
    { fetchXslt, xsltUpdated, saveXslt },
  ),
  lifecycle({
    componentDidMount() {
      const { fetchXslt, xsltId } = this.props;

      fetchXslt(xsltId);
    },
  }),
  branch(({ xslt }) => !xslt, renderComponent(() => <Loader active>Loading XSLT</Loader>)),
);

const XsltEditor = enhance(({ xsltId, xslt, xsltUpdated }) => (
  <div>
    XsltEditor
    <SaveXslt xsltId={xsltId} />
    <AceEditor
      name={`${xsltId}-ace-editor`}
      mode="xml"
      theme="github"
      value={xslt.xsltData}
      onChange={(newValue) => {
        if (newValue !== xslt.xsltData) xsltUpdated(xsltId, newValue);
      }}
    />
  </div>
));

XsltEditor.propTypes = {
  xsltId: PropTypes.string.isRequired,
};

export default XsltEditor;
