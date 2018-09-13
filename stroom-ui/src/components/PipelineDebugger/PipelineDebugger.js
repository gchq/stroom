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
import { compose, lifecycle } from 'recompose';
import { connect } from 'react-redux';

import { Pipeline } from 'components/PipelineEditor';
import { actionCreators as pipelineActionCreators } from 'components/PipelineEditor';

import { actionCreators } from './redux';
import DebuggerControls from './DebuggerControls';
import DebuggerStep from './DebuggerStep';

const { startDebugging } = actionCreators;

const { selectNextPipelineElement } = pipelineActionCreators;

const enhance = compose(
  connect(
    ({ debuggers }, { debuggerId }) => ({ debugger: debuggers[debuggerId], debuggerId }),
    { startDebugging, selectNextPipelineElement },
  ),
  lifecycle({
    componentDidMount() {
      const { debuggerId, pipelineId, startDebugging, selectNextPipelineElement } = this.props;

      selectNextPipelineElement(pipelineId);
      startDebugging(debuggerId, pipelineId);
    }
  }),
);

const PipelineDebugger = ({ pipelineId, debuggerId }) => (
  <div className="pipeline-debugger">
    <DebuggerControls debuggerId={debuggerId} />
    <Pipeline pipelineId={pipelineId} onElementSelected={() => { }} />
    <DebuggerStep debuggerId={debuggerId} />
  </div>
);

PipelineDebugger.propTypes = {
  debuggerId: PropTypes.string.isRequired,
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(PipelineDebugger);