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
import { compose, lifecycle, branch, renderComponent, withHandlers } from 'recompose';
import { connect } from 'react-redux';

import Button from 'components/Button';
import { Pipeline } from 'components/PipelineEditor';
import Loader from 'components/Loader';
import { actionCreators as pipelineActionCreators, fetchPipeline } from 'components/PipelineEditor';

import { actionCreators } from './redux';
import DebuggerStep from './DebuggerStep';
import { getNext, getPrevious } from './pipelineDebugger.utils';

const { startDebugging } = actionCreators;

const { pipelineElementSelected } = pipelineActionCreators;

const enhance = compose(
  connect(
    ({ debuggers, pipelineEditor: { pipelineStates } }, { debuggerId, pipelineId }) => ({
      debuggerState: debuggers[debuggerId],
      debuggerId,
      pipelineState: pipelineStates[pipelineId],
    }),
    { startDebugging, pipelineElementSelected, fetchPipeline },
  ),
  lifecycle({
    componentDidMount() {
      const { debuggerId, pipelineId, startDebugging, pipelineElementSelected, fetchPipeline } = this.props;
      fetchPipeline(pipelineId);
      startDebugging(debuggerId, pipelineId);
    }
  }),
  branch(
    ({ debuggerState }) => !debuggerState,
    renderComponent(() => <Loader message="Loading pipeline..." />),
  ),
  withHandlers({
    onNext: ({ pipelineState, pipelineElementSelected, pipelineId, debuggerState, currentElementId }) => () => {
      const nextElementId = getNext(pipelineState);
      pipelineElementSelected(pipelineId, nextElementId);
    },
    onPrevious: ({ pipelineState, pipelineElementSelected, pipelineId, debuggerState, currentElementId }) => () => {
      const nextElementId = getPrevious(pipelineState);
      pipelineElementSelected(pipelineId, nextElementId);
    },
  }),
);

const PipelineDebugger = ({ pipelineId, debuggerId, onNext, onPrevious }) => (
  <div className="pipeline-debugger">
    <div>
      <Button icon='chevron-left' text='Previous' onClick={onPrevious} />
      <Button icon='chevron-right' text='Next' onClick={onNext} />
    </div>
    <Pipeline
      pipelineId={pipelineId}
      onElementSelected={() => { }} />
    <DebuggerStep debuggerId={debuggerId} />
  </div>
);

PipelineDebugger.propTypes = {
  debuggerId: PropTypes.string.isRequired,
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(PipelineDebugger);