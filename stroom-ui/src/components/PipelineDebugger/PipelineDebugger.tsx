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

import * as React from "react";
import {
  compose,
  lifecycle,
  branch,
  renderComponent,
  withHandlers
} from "recompose";
import { connect } from "react-redux";

import Button from "../Button";
import { Pipeline } from "../PipelineEditor";
import { StoreStateById as PipelineStatesStatePerId } from "../PipelineEditor/redux/pipelineStatesReducer";
import Loader from "../Loader";
import {
  actionCreators as pipelineActionCreators,
  fetchPipeline
} from "../PipelineEditor";

import {
  actionCreators,
  StoreStateById as DebuggerStoreStatePerId
} from "./redux";
import DebuggerStep from "./DebuggerStep";
import { getNext, getPrevious } from "./pipelineDebugger.utils";
import { GlobalStoreState } from "../../startup/reducers";

const { startDebugging } = actionCreators;

const { pipelineElementSelected } = pipelineActionCreators;

export interface Props {
  debuggerId: string;
  pipelineId: string;
}

interface ConnectState {
  pipelineState: PipelineStatesStatePerId;
  debuggerState: DebuggerStoreStatePerId;
}
interface ConnectDispatch {
  startDebugging: typeof startDebugging;
  pipelineElementSelected: typeof pipelineElementSelected;
  fetchPipeline: typeof fetchPipeline;
}
interface WithHandlers {
  onNext: () => void;
  onPrevious: () => void;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithHandlers {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    (
      { debuggers, pipelineEditor: { pipelineStates } },
      { debuggerId, pipelineId }
    ) => ({
      debuggerState: debuggers[debuggerId],
      pipelineState: pipelineStates[pipelineId]
    }),
    { startDebugging, pipelineElementSelected, fetchPipeline }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      const {
        debuggerId,
        pipelineId,
        startDebugging,
        fetchPipeline
      } = this.props;
      fetchPipeline(pipelineId);
      startDebugging(debuggerId, pipelineId);
    }
  }),
  branch(
    ({ debuggerState }) => !debuggerState,
    renderComponent(() => <Loader message="Loading pipeline..." />)
  ),
  withHandlers<Props & ConnectState & ConnectDispatch, WithHandlers>({
    onNext: ({ pipelineState, pipelineElementSelected, pipelineId }) => () => {
      const nextElementId = getNext(pipelineState);
      pipelineElementSelected(pipelineId, nextElementId);
    },
    onPrevious: ({
      pipelineState,
      pipelineElementSelected,
      pipelineId
    }) => () => {
      const nextElementId = getPrevious(pipelineState);
      pipelineElementSelected(pipelineId, nextElementId);
    }
  })
);

const PipelineDebugger = ({
  pipelineId,
  debuggerId,
  onNext,
  onPrevious
}: EnhancedProps) => (
  <div className="PipelineDebugger">
    <div>
      <Button icon="chevron-left" text="Previous" onClick={onPrevious} />
      <Button icon="chevron-right" text="Next" onClick={onNext} />
    </div>
    <Pipeline pipelineId={pipelineId} onElementSelected={() => {}} />
    <DebuggerStep debuggerId={debuggerId} />
  </div>
);

export default enhance(PipelineDebugger);
