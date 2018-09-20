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
import { compose, lifecycle, branch, renderComponent, withProps } from 'recompose';
import { withRouter } from 'react-router-dom';
import { connect } from 'react-redux';

import Loader from 'components/Loader'
import { LineContainer, LineTo } from 'components/LineTo';
import { mapObject } from 'lib/treeUtils';
import PipelineElement from './PipelineElement';
import { fetchPipeline } from './pipelineResourceClient';
import { fetchElements, fetchElementProperties } from './elementResourceClient';
import lineElementCreators from './pipelineLineElementCreators';
import { getPipelineLayoutInformation } from './pipelineUtils';


const HORIZONTAL_SPACING = 150;
const VERTICAL_SPACING = 70;
const HORIZONTAL_START_PX = 10;
const VERTICAL_START_PX = 10;
const COMMON_ELEMENT_STYLE = {
  position: 'absolute',
};

const enhance = compose(
  withRouter,
  connect(
    (
      { pipelineEditor: { pipelineStates, elements } },
      { pipelineId },
    ) => ({
      pipelineState: pipelineStates[pipelineId],
      elements,
    }),
    {
      // action, needed by lifecycle hook below
      fetchPipeline,
      fetchElements,
      fetchElementProperties,
    },
  ),
  lifecycle({
    componentDidMount() {
      const {
        fetchElements, fetchElementProperties, fetchPipeline, pipelineId,
      } = this.props;

      fetchElements();
      fetchElementProperties();
      fetchPipeline(pipelineId);
    },
  }),
  branch(
    ({ pipelineState, elements: { elements } }) => !(pipelineState && pipelineState.pipeline && elements),
    renderComponent(() => <Loader message="Loading pipeline..." />),
  ),
  withProps(({ pipelineState: { asTree, pipeline } }) => ({
    elementStyles: mapObject(getPipelineLayoutInformation(asTree), (l) => {
      const index = l.verticalPos - 1;
      const fromTop = VERTICAL_START_PX + index * VERTICAL_SPACING;
      const fromLeft = HORIZONTAL_START_PX + l.horizontalPos * HORIZONTAL_SPACING;

      return {
        ...COMMON_ELEMENT_STYLE,
        top: `${fromTop}px`,
        left: `${fromLeft}px`,
      };
    }),
    pipeline,
  })),
);

const Pipeline = ({
  pipelineId,
  elementStyles,
  pipeline,
  onElementSelected,
}) => {
  return (
    <LineContainer
      className="Pipeline-editor__graph flat"
      lineContextId={`pipeline-lines-${pipelineId}`}
      lineElementCreators={lineElementCreators}
    >
      <div className="Pipeline-editor__elements">
        {Object.keys(elementStyles)
          .map(es => pipeline.merged.elements.add.find(e => e.id === es))
          .map(e => (
            <div key={e.id} id={e.id} style={elementStyles[e.id]}>
              <PipelineElement
                pipelineId={pipelineId}
                elementId={e.id}
                onClick={onElementSelected}
              />
            </div>
          ))}
      </div>
      <div className="Pipeline-editor__lines">
        {pipeline.merged.links.add
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
}

Pipeline.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  onElementSelected: PropTypes.func.isRequired
}

export default enhance(Pipeline);
