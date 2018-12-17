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
  withProps
} from "recompose";
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
import { PipelineElementType } from "src/types";

export interface Props {
  pipelineId: string;
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

interface WithProps {
  layoutGrid: PipelineLayoutGrid;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithProps {}

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
  ),
  lifecycle<Props & ConnectState & ConnectDispatch, {}>({
    componentDidMount() {
      const {
        fetchElements,
        fetchElementProperties,
        fetchPipeline,
        pipelineId
      } = this.props;

      fetchElements();
      fetchElementProperties();
      fetchPipeline(pipelineId);
    }
  }),
  branch(
    ({ pipelineState, elements: { elements } }) =>
      !(pipelineState && pipelineState.pipeline && elements),
    renderComponent(() => <Loader message="Loading pipeline..." />)
  ),
  withProps(({ pipelineState: { asTree } }) => ({
    layoutGrid: getPipelineLayoutGrid(asTree)
  }))
);

const Pipeline = ({
  pipelineId,
  layoutGrid,
  pipelineState: { pipeline }
}: EnhancedProps) => (
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
                  />
                ))}
            {column.cellType == CellType.ELBOW && <ElbowLine />}
          </div>
        ))}
      </div>
    ))}
  </div>
);

export default enhance(Pipeline);
