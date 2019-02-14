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
import AddElementModal, {
  useDialog as useAddElementDialog
} from "./AddElementModal";
import { Props as ButtonProps } from "../Button";
import PipelineSettings, {
  useDialog as usePipelineSettingsDialog
} from "./PipelineSettings";
import ElementPalette from "./ElementPalette";
import DeletePipelineElement, {
  useDialog as useDeleteElementDialog
} from "./DeletePipelineElement";
import { ElementDetails } from "./ElementDetails";
import { fetchPipeline, savePipeline } from "./pipelineResourceClient";
import { actionCreators } from "./redux";
import Pipeline from "./Pipeline";
import { GlobalStoreState } from "../../startup/reducers";
import { StoreStateById as PipelineStatesStoreStateById } from "./redux/pipelineStatesReducer";
import DocRefEditor from "../DocRefEditor";

const {
  pipelineElementSelectionCleared,
  pipelineSettingsUpdated,
  pipelineElementAdded,
  pipelineElementDeleted
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
  pipelineElementSelectionCleared: typeof pipelineElementSelectionCleared;
  pipelineSettingsUpdated: typeof pipelineSettingsUpdated;
  pipelineElementAdded: typeof pipelineElementAdded;
  pipelineElementDeleted: typeof pipelineElementDeleted;
}
export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ pipelineEditor: { pipelineStates } }, { pipelineId }) => ({
      pipelineState: pipelineStates[pipelineId]
    }),
    {
      fetchPipeline,
      savePipeline,
      pipelineElementSelectionCleared,
      pipelineSettingsUpdated,
      pipelineElementAdded,
      pipelineElementDeleted
    }
  )
);

const PipelineEditor = ({
  pipelineId,
  pipelineState,
  pipelineElementSelectionCleared,
  pipelineSettingsUpdated,
  pipelineElementAdded,
  pipelineElementDeleted,
  savePipeline,
  fetchPipeline
}: EnhancedProps) => {
  useEffect(() => {
    fetchPipeline(pipelineId);
  });

  const {
    showDialog: showSettingsDialog,
    componentProps: settingsComponentProps
  } = usePipelineSettingsDialog(description =>
    pipelineSettingsUpdated(pipelineId, description)
  );

  const {
    showDialog: showAddElementDialog,
    componentProps: addElementComponentProps
  } = useAddElementDialog((parentId, elementDefinition, name) => {
    pipelineElementAdded(pipelineId, parentId, elementDefinition, name);
  });

  const {
    showDialog: showDeleteElementDialog,
    componentProps: deleteElementComponentProps
  } = useDeleteElementDialog(elementIdToDelete => {
    pipelineElementDeleted(pipelineId, elementIdToDelete);
  });

  if (!(pipelineState && pipelineState.pipeline)) {
    return <Loader message="Loading pipeline..." />;
  }

  const { selectedElementId, isDirty, isSaving } = pipelineState;

  const actionBarItems: Array<ButtonProps> = [
    {
      icon: "cogs",
      title: "Open Settings",
      onClick: () =>
        showSettingsDialog(pipelineState!.pipeline!.description || "something")
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
      onClick: () =>
        console.log("TODO - Implement Selection of Parent Pipeline")
    }
  ];

  return (
    <DocRefEditor docRefUuid={pipelineId} actionBarItems={actionBarItems}>
      <div className="Pipeline-editor">
        <AddElementModal {...addElementComponentProps} />
        <DeletePipelineElement {...deleteElementComponentProps} />
        <PipelineSettings {...settingsComponentProps} />
        <div className="Pipeline-editor__element-palette">
          <ElementPalette
            pipelineId={pipelineId}
            showDeleteElementDialog={showDeleteElementDialog}
          />
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
            <Pipeline
              pipelineId={pipelineId}
              showAddElementDialog={showAddElementDialog}
            />
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
