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
import { useEffect } from "react";
import { compose } from "recompose";
import { connect } from "react-redux";
import PanelGroup from "react-panelgroup";

import Loader from "../Loader";
import AddElementModal from "./AddElementModal";
import { Props as ButtonProps } from "../Button";
import PipelineSettings from "./PipelineSettings";
import ElementPalette from "./ElementPalette";
import DeletePipelineElement from "./DeletePipelineElement";
import { ElementDetails } from "./ElementDetails";
import { fetchPipeline, savePipeline } from "./pipelineResourceClient";
import { actionCreators } from "./redux";
import Pipeline from "./Pipeline";
import { GlobalStoreState } from "../../startup/reducers";
import { StoreStateById as PipelineStatesStoreStateById } from "./redux/pipelineStatesReducer";
import DocRefEditor from "../DocRefEditor";

const {
  startInheritPipeline,
  pipelineSettingsOpened,
  pipelineElementSelectionCleared
} = actionCreators;

export interface Props {
  pipelineId: string;
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
export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
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
  )
);

const PipelineEditor = ({
  pipelineId,
  pipelineState,
  pipelineElementSelectionCleared,
  savePipeline,
  startInheritPipeline,
  pipelineSettingsOpened,
  fetchPipeline
}: EnhancedProps) => {
  useEffect(() => {
    fetchPipeline(pipelineId);
  });

  if (!(pipelineState && pipelineState.pipeline)) {
    return <Loader message="Loading pipeline..." />;
  }

  const { selectedElementId, isDirty, isSaving } = pipelineState;

  const actionBarItems: Array<ButtonProps> = [
    {
      icon: "cogs",
      title: "Open Settings",
      onClick: () => pipelineSettingsOpened(pipelineId)
    },
    {
      icon: "save",
      disabled: !(isDirty || isSaving),
      title: isSaving ? "Saving..." : isDirty ? "Save" : "Saved",
      onClick: () => savePipeline(pipelineId)
    },
    {
      icon: "recycle",
      title: "Create Child Pipeline",
      onClick: () => startInheritPipeline(pipelineId)
    }
  ];

  return (
    <DocRefEditor docRefUuid={pipelineId} actionBarItems={actionBarItems}>
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
              onClose={() => pipelineElementSelectionCleared(pipelineId)}
            />
          ) : (
            <div />
          )}
        </PanelGroup>
      </div>
    </DocRefEditor>
  );
};

export default enhance(PipelineEditor);
