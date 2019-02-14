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

import Loader from "../Loader";
import PipelineElement from "./PipelineElement";
import ElbowLine from "./ElbowLine/ElbowLine";
import { fetchPipeline } from "./pipelineResourceClient";
import { fetchElements, fetchElementProperties } from "./elementResourceClient";
import {
  getPipelineLayoutGrid,
  PipelineLayoutGrid,
  CellType
} from "./pipelineUtils";
import { StoreState as ElementStoreState } from "./redux/elementReducer";
import { StoreStateById as PipelineStatesStoreStateById } from "./redux/pipelineStatesReducer";
import { GlobalStoreState } from "../../startup/reducers";
import { PipelineElementType } from "../../types";
import { getAllElementNames } from "./pipelineUtils";
import { ShowDialog as ShowAddElementDialog } from "./AddElementModal";

export interface Props {
  pipelineId: string;
  showAddElementDialog: ShowAddElementDialog;
}

interface ConnectState {
  elements: ElementStoreState;
  pipelineState: PipelineStatesStoreStateById;
}

interface ConnectDispatch {
  fetchPipeline: typeof fetchPipeline;
  fetchElements: typeof fetchElements;
  fetchElementProperties: typeof fetchElementProperties;
}

export interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ pipelineEditor: { pipelineStates, elements } }, { pipelineId }) => ({
      pipelineState: pipelineStates[pipelineId],
      elements
    }),
    {
      fetchPipeline,
      fetchElements,
      fetchElementProperties
    }
  )
);

const Pipeline = ({
  pipelineId,
  pipelineState,
  elements: { elements },
  fetchElements,
  fetchElementProperties,
  fetchPipeline,
  showAddElementDialog
}: EnhancedProps) => {
  useEffect(() => {
    fetchElements();
    fetchElementProperties();
    fetchPipeline(pipelineId);
  }, []);

  if (!(pipelineState && pipelineState.pipeline && elements)) {
    return <Loader message="Loading pipeline..." />;
  }

  const { pipeline, asTree } = pipelineState;

  if (!asTree) {
    return <Loader message="Awaiting pipeline tree model..." />;
  }

  const existingNames = getAllElementNames(pipeline);

  const layoutGrid: PipelineLayoutGrid = getPipelineLayoutGrid(asTree);

  return (
    <div className="Pipeline-editor__elements">
      {layoutGrid.rows.map((row, r) => (
        <div key={r} className="Pipeline-editor__elements-row">
          {row.columns.map((column, c) => (
            <div
              key={c}
              className={`Pipeline-editor__elements_cell ${
                CellType[column.cellType]
              }`}
            >
              {column.cellType == CellType.ELEMENT &&
                pipeline &&
                pipeline.merged.elements.add &&
                pipeline.merged.elements.add
                  .filter((e: PipelineElementType) => e.id === column.uuid)
                  .map(e => (
                    <PipelineElement
                      key={e.id}
                      pipelineId={pipelineId}
                      elementId={e.id}
                      showAddElementDialog={showAddElementDialog}
                      existingNames={existingNames}
                    />
                  ))}
              {column.cellType == CellType.ELBOW && <ElbowLine north east />}
            </div>
          ))}
        </div>
      ))}
    </div>
  );
};

export default enhance(Pipeline);
