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
import { withRouter, RouteComponentProps } from "react-router-dom";
import { connect } from "react-redux";
import PanelGroup from "react-panelgroup";

import { DocRefIconHeader } from "../IconHeader";
import Loader from "../Loader";
import AddElementModal from "./AddElementModal";
import DocRefBreadcrumb from "../DocRefBreadcrumb";
import SavePipeline from "./SavePipeline";
import CreateChildPipeline from "./CreateChildPipeline";
import OpenPipelineSettings from "./OpenPipelineSettings";
import PipelineSettings from "./PipelineSettings";
import ElementPalette from "./ElementPalette";
import DeletePipelineElement from "./DeletePipelineElement";
import { ElementDetails } from "./ElementDetails";
import { fetchPipeline, savePipeline } from "./pipelineResourceClient";
import { actionCreators } from "./redux";
import Pipeline from "./Pipeline";
import { GlobalStoreState } from "../../startup/reducers";
import { DocRefConsumer } from "../../types";
import { StoreStateById as PipelineStatesStoreStateById } from "./redux/pipelineStatesReducer";

const {
  startInheritPipeline,
  pipelineSettingsOpened,
  pipelineElementSelectionCleared
} = actionCreators;

export interface Props {
  pipelineId: string;
}
interface WithHandlers {
  openDocRef: DocRefConsumer;
}
interface ConnectState {
  pipelineState: PipelineStatesStoreStateById;
}
interface ConnectDispatch {
  fetchPipeline: typeof fetchPipeline;
  savePipeline: typeof savePipeline;
  startInheritPipeline: typeof startInheritPipeline;
  pipelineSettingsOpened: typeof pipelineSettingsOpened;
  pipelineElementSelectionCleared: typeof pipelineElementSelectionCleared;
}

export interface EnhancedProps
  extends Props,
    RouteComponentProps<any>,
    WithHandlers,
    ConnectState,
    ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  withRouter,
  withHandlers<Props & RouteComponentProps<any>, WithHandlers>({
    openDocRef: ({ history }) => d => history.push(`/s/doc/${d.type}/${d.uuid}`)
  }),
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ pipelineEditor: { pipelineStates } }, { pipelineId }) => ({
      pipelineState: pipelineStates[pipelineId]
    }),
    {
      // action, needed by lifecycle hook below
      fetchPipeline,
      savePipeline,
      startInheritPipeline,
      pipelineSettingsOpened,
      pipelineElementSelectionCleared
    }
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      const { fetchPipeline, pipelineId } = this.props;
      fetchPipeline(pipelineId);
    }
  }),
  branch(
    ({ pipelineState }) => !(pipelineState && pipelineState.pipeline),
    renderComponent(() => <Loader message="Loading pipeline..." />)
  )
);

const PipelineEditor = ({
  pipelineId,
  pipelineState: { selectedElementId, pipeline, isDirty },
  openDocRef,
  savePipeline,
  startInheritPipeline,
  pipelineSettingsOpened,
  pipelineElementSelectionCleared
}: EnhancedProps) => (
  <div className="pipeline-editor__container">
    <div className="pipeline-editor__header">
      <div className="pipeline-editor__header__title">
        <DocRefIconHeader
          docRefType={pipeline!.docRef.type}
          text={pipeline!.docRef.name || "UNKNOWN_NAME"}
        />
        <DocRefBreadcrumb docRefUuid={pipelineId} openDocRef={openDocRef} />
      </div>
      <div className="pipeline-editor__header__actions">
        <SavePipeline
          pipelineId={pipelineId}
          isDirty={isDirty}
          savePipeline={savePipeline}
        />
        <CreateChildPipeline
          pipelineId={pipelineId}
          startInheritPipeline={startInheritPipeline}
        />
        <OpenPipelineSettings
          pipelineId={pipelineId}
          pipelineSettingsOpened={pipelineSettingsOpened}
        />
      </div>
    </div>
    <div className="Pipeline-editor">
      <AddElementModal pipelineId={pipelineId} />
      <DeletePipelineElement pipelineId={pipelineId} />
      <PipelineSettings pipelineId={pipelineId} />
      <div className="Pipeline-editor__element-palette">
        <ElementPalette pipelineId={pipelineId} />
      </div>

      <PanelGroup
        direction="column"
        className="Pipeline-editor__content"
        panelWidths={[
          {},
          {
            resize: "dynamic",
            size: selectedElementId !== undefined ? "50%" : 0
          }
        ]}
      >
        <div className="Pipeline-editor__topPanel">
          <Pipeline pipelineId={pipelineId} />
        </div>
        {selectedElementId !== undefined ? (
          <ElementDetails
            pipelineId={pipelineId}
            onClose={() => pipelineElementSelectionCleared(false)}
          />
        ) : (
          <div />
        )}
      </PanelGroup>
    </div>
  </div>
);

export default enhance(PipelineEditor);
