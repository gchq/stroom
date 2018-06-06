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

import { compose } from 'recompose';
import { connect } from 'react-redux';

import { Confirm } from 'semantic-ui-react';

import { LineContainer, LineTo } from 'components/LineTo';

import { mapObject } from 'lib/treeUtils';

import { withPipeline } from './withPipeline';

import { actionCreators } from './redux';

import PipelineElement from './PipelineElement';
import PipelineElementSettings from './PipelineElementSettings';
import { AddElementWizard } from './AddElementToPipeline';

import lineElementCreators from './pipelineLineElementCreators';

const { confirmDeletePipelineElement, cancelDeletePipelineElement } = actionCreators;

const HORIZONTAL_SPACING = 150;
const VERTICAL_SPACING = 50;
const HORIZONTAL_START_PX = 50;
const VERTICAL_START_PX = 50;
const COMMON_ELEMENT_STYLE = {
  position: 'absolute',
};

const PipelineEditor = ({
  pipelineId,
  pipeline,
  pendingElementIdToDelete,
  cancelDeletePipelineElement,
  confirmDeletePipelineElement,
  layoutInformation,
}) => {
  const elementStyles = mapObject(layoutInformation, l => ({
    ...COMMON_ELEMENT_STYLE,
    top: `${VERTICAL_START_PX + l.verticalPos * VERTICAL_SPACING}px`,
    left: `${HORIZONTAL_START_PX + l.horizontalPos * HORIZONTAL_SPACING}px`,
  }));

  return (
    <div className="Pipeline-editor">
      <AddElementWizard pipelineId={pipelineId} />
      <Confirm
        open={!!pendingElementIdToDelete}
        content="This will delete the element from the pipeline, are you sure?"
        onCancel={() => cancelDeletePipelineElement(pipelineId)}
        onConfirm={() => confirmDeletePipelineElement(pipelineId, pendingElementIdToDelete)}
      />
      <LineContainer
        className="Pipeline-editor__overview"
        lineContextId={`pipeline-lines-${pipelineId}`}
        lineElementCreators={lineElementCreators}
      >
        <h4>Pipeline Editor {pipelineId}</h4>
        {pipeline.elements.add.map(e => (
          <div key={e.id} id={e.id} style={elementStyles[e.id]}>
            <PipelineElement pipelineId={pipelineId} elementId={e.id} />
          </div>
        ))}
        {pipeline.links.add
          .map(l => ({ ...l, lineId: `${l.from}-${l.to}` }))
          .map(l => (
            <LineTo lineId={l.lineId} key={l.lineId} fromId={l.from} toId={l.to} lineType="curve" />
          ))}
      </LineContainer>

      <div className="Pipeline-editor__settings">
        <PipelineElementSettings pipelineId={pipelineId} />
      </div>
    </div>
  );
};

PipelineEditor.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  pipeline: PropTypes.object.isRequired,
  asTree: PropTypes.object.isRequired,
  layoutInformation: PropTypes.object.isRequired,
};

export default compose(
  connect(
    state => ({
      // state
    }),
    {
      confirmDeletePipelineElement,
      cancelDeletePipelineElement,
    },
  ),
  withPipeline(),
)(PipelineEditor);
