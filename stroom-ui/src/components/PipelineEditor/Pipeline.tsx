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
import { LineContainer, LineTo } from "../LineTo";
import { mapObject } from "../../lib/treeUtils";
import PipelineElement from "./PipelineElement";
import { fetchPipeline } from "./pipelineResourceClient";
import { fetchElements, fetchElementProperties } from "./elementResourceClient";
import lineElementCreators from "./pipelineLineElementCreators";
import {
  getPipelineLayoutInformation,
  PipelineLayoutInfo
} from "./pipelineUtils";
import { PipelineElementType } from "../../types";
import { StoreState as ElementStoreState } from "./redux/elementReducer";
import { StoreStateById as PipelineStatesStoreStateById } from "./redux/pipelineStatesReducer";
import { GlobalStoreState } from "../../startup/reducers";

const HORIZONTAL_SPACING = 150;
const VERTICAL_SPACING = 70;
const HORIZONTAL_START_PX = 10;
const VERTICAL_START_PX = 10;
const COMMON_ELEMENT_STYLE = {
  position: "absolute"
};

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
  elementStyles: {
    [uuid: string]: React.HTMLAttributes<HTMLDivElement>;
  };
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
    elementStyles: mapObject(
      getPipelineLayoutInformation(asTree),
      (l: PipelineLayoutInfo) => {
        const index: number = l.verticalPos - 1;
        const fromTop = VERTICAL_START_PX + index * VERTICAL_SPACING;
        const fromLeft =
          HORIZONTAL_START_PX + l.horizontalPos * HORIZONTAL_SPACING;

        return {
          ...COMMON_ELEMENT_STYLE,
          top: `${fromTop}px`,
          left: `${fromLeft}px`
        };
      }
    )
  }))
);

const Pipeline = ({
  pipelineId,
  elementStyles,
  pipelineState: { pipeline }
}: EnhancedProps) => {
  return (
    <LineContainer
      className="Pipeline-editor__graph flat"
      lineContextId={`pipeline-lines-${pipelineId}`}
      lineElementCreators={lineElementCreators}
    >
      <div className="Pipeline-editor__elements">
        {Object.keys(elementStyles)
          .map(
            es =>
              pipeline &&
              pipeline.merged.elements.add &&
              pipeline.merged.elements.add.find(
                (e: PipelineElementType) => e.id === es
              )
          )
          .filter(e => e !== undefined)
          .map(e => (
            <div key={e!.id} id={e!.id} style={elementStyles[e!.id]}>
              <PipelineElement pipelineId={pipelineId} elementId={e!.id} />
            </div>
          ))}
      </div>
      <div className="Pipeline-editor__lines">
        {pipeline &&
          pipeline.merged.links.add &&
          pipeline.merged.links.add
            .filter(l => elementStyles[l.from] && elementStyles[l.to])
            .map(l => ({ ...l, lineId: `${l.from}-${l.to}` }))
            .map(l => (
              <LineTo
                lineId={l.lineId}
                key={l.lineId}
                fromId={l.from}
                toId={l.to}
                lineType="curve"
              />
            ))}
      </div>
    </LineContainer>
  );
};

export default enhance(Pipeline);
