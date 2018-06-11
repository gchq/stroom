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

import { Header, Button, Icon, Image, Input } from 'semantic-ui-react';

import { compose, withState } from 'recompose';
import { connect } from 'react-redux';
import { LineContainer, LineTo } from 'components/LineTo';
import { mapObject } from 'lib/treeUtils';
import { withPipeline } from './withPipeline';
import { actionCreators } from './redux';

import PipelineElement from './PipelineElement';
import PipelineElementSettings from './PipelineElementSettings';
import { AddElementWizard } from './AddElementToPipeline';
import { ElementPalette } from './ElementPalette';

import lineElementCreators from './pipelineLineElementCreators';

const HORIZONTAL_SPACING = 150;
const VERTICAL_SPACING = 50;
const HORIZONTAL_START_PX = 50;
const VERTICAL_START_PX = 50;
const COMMON_ELEMENT_STYLE = {
  position: 'absolute',
};

const withPaletteOpen = withState('isPaletteOpen', 'setPaletteOpen', false);

const PipelineEditor = ({
  pipelineId,
  pipeline,
  pendingElementIdToDelete,
  layoutInformation,
  isPaletteOpen,
  setPaletteOpen,
  elementsByCategory,
}) => {
  const togglePaletteOpen = () => setPaletteOpen(!isPaletteOpen);

  const elementStyles = mapObject(layoutInformation, l => ({
    ...COMMON_ELEMENT_STYLE,
    top: `${VERTICAL_START_PX + l.verticalPos * VERTICAL_SPACING}px`,
    left: `${HORIZONTAL_START_PX + l.horizontalPos * HORIZONTAL_SPACING}px`,
  }));

  const handleKeyPress = (e) => {
    const k = e.key;
    switch (k) {
      case 'x':
        toggleRecycleBin();
        break;
      case 'e':
        togglePaletteOpen();
        break;
    }
  };

  let className = 'Pipeline-editor';

  if (isPaletteOpen) {
    className += ' Pipeline-editor--palette-open';
  } else {
    className += ' Pipeline-editor--palette-close';
  }

  return (
    <div className={className}>
      <div className="Pipeline-editor__element-palette">
        <ElementPalette />
      </div>
      <button className="Pipeline-editor__palette-toggle" onClick={togglePaletteOpen}>
        {isPaletteOpen ? '<' : '>'}
      </button>

      <LineContainer
        className="Pipeline-editor__graph"
        lineContextId={`pipeline-lines-${pipelineId}`}
        lineElementCreators={lineElementCreators}
      >
        <Header as="h4">Pipeline Editor {pipelineId}</Header>
        <div className="Pipeline-editor__elements">
          {pipeline.elements.add.map(e => (
            <div key={e.id} id={e.id} style={elementStyles[e.id]}>
              <PipelineElement pipelineId={pipelineId} elementId={e.id} />
            </div>
          ))}
        </div>
        <div className="Pipeline-editor__lines">
          {pipeline.links.add
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
    </div>
  );
};

PipelineEditor.propTypes = {
  pipelineId: PropTypes.string.isRequired,
  pipeline: PropTypes.object.isRequired,
  asTree: PropTypes.object.isRequired,
  layoutInformation: PropTypes.object.isRequired,
  elementsByCategory: PropTypes.object.isRequired,

  // withPaletteOpen
  isPaletteOpen: PropTypes.bool.isRequired,
  setPaletteOpen: PropTypes.func.isRequired,
};

export default compose(
  connect(
    state => ({
      elementsByCategory: state.elements.byCategory || {},
    }),
    {
      // actions
    },
  ),
  withPipeline(),
  withPaletteOpen,
)(PipelineEditor);
