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

import { compose, lifecycle, withState, branch, renderComponent, withProps } from 'recompose';
import { connect } from 'react-redux';
import { Icon, Confirm, Loader } from 'semantic-ui-react';

import PanelGroup from 'react-panelgroup';

import { LineContainer, LineTo } from 'components/LineTo';
import { mapObject } from 'lib/treeUtils';
import { getPipelineLayoutInformation } from './pipelineUtils';

import PipelineSettings from './PipelineSettings';
import PipelineElement from './PipelineElement';
import ElementPalette from './ElementPalette';
import Bin from './Bin';

import lineElementCreators from './pipelineLineElementCreators';
import { ElementDetails } from './ElementDetails';

import { fetchPipeline } from './pipelineResourceClient';
import { fetchElements, fetchElementProperties } from './elementResourceClient';
import { withConfig } from 'startup/config';
import { actionCreators } from './redux';

const {
  pipelineElementDeleteCancelled,
  pipelineElementDeleted
} = actionCreators;

const HORIZONTAL_SPACING = 150;
const VERTICAL_SPACING = 70;
const HORIZONTAL_START_PX = 10;
const VERTICAL_START_PX = 10;
const COMMON_ELEMENT_STYLE = {
  position: 'absolute',
};

const withPaletteOpen = withState('isPaletteOpen', 'setPaletteOpen', true);
const withElementDetailsOpen = withState('isElementDetailsOpen', 'setElementDetailsOpen', false);

const enhance = compose(
  withConfig,
  connect(
    (state, props) => ({
      pipeline: state.pipelineEditor.pipelines[props.pipelineId],
      elements: state.pipelineEditor.elements,
    }),
    {
      // action, needed by lifecycle hook below
      fetchPipeline,
      fetchElements,
      fetchElementProperties,
      pipelineElementDeleteCancelled,
      pipelineElementDeleted
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
    ({ pipeline }) => !pipeline,
    renderComponent(() => <Loader active>Loading Pipeline</Loader>),
  ),
  branch(
    ({ pipeline: { pipeline } }) => !pipeline,
    renderComponent(() => <Loader active>Loading Pipeline Data</Loader>),
  ),
  branch(
    ({ elements: { elements } }) => !elements,
    renderComponent(() => <Loader active>Loading Elements</Loader>),
  ),
  withPaletteOpen,
  withElementDetailsOpen,
  withProps(({
    pipelineId,
    pipeline: { asTree, pendingElementToDelete },
    setPaletteOpen,
    isPaletteOpen,
    pipelineElementDeleteCancelled,
    pipelineElementDeleted,
  }) => ({
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
    onCancelDelete: () => pipelineElementDeleteCancelled(pipelineId),
    onConfirmDelete: () => {
      pipelineElementDeleted(pipelineId, pendingElementToDelete);
    },
  })),
);

const PipelineEditor = ({
  pipelineId,
  pipeline: {
    pipeline, isDirty, isSaving, pendingElementToDelete,
  },
  isPaletteOpen,
  setPaletteOpen,
  isElementDetailsOpen,
  setElementDetailsOpen,
  editorClassName,
  elementStyles,
  onCancelDelete,
  onConfirmDelete,
}) => {
  const togglePaletteOpen = () => setPaletteOpen(!isPaletteOpen);
  const settingsPanelSize = isElementDetailsOpen ? '50%' : 0;
  const panelSizes = [
    {},
    {
      resize: 'dynamic',
      size: settingsPanelSize,
    },
  ];
  return (
    <div className={`Pipeline-editor Pipeline-editor--palette-${isPaletteOpen ? 'open' : 'close'}`}>
      <Confirm
        open={!!pendingElementToDelete}
        content={`Delete ${pendingElementToDelete} from pipeline?`}
        onCancel={onCancelDelete}
        onConfirm={onConfirmDelete}
      />
      <PipelineSettings pipelineId={pipelineId} />
      <div className="Pipeline-editor__element-palette">
        <ElementPalette pipelineId={pipelineId} />
      </div>

      <button className="Pipeline-editor__palette-toggle" onClick={togglePaletteOpen}>
        {isPaletteOpen ? <Icon name="caret left" /> : <Icon name="caret right" />}
      </button>

      <PanelGroup direction="column" className="Pipeline-editor__content" panelWidths={panelSizes}>
        <div className="Pipeline-editor__topPanel">
          <LineContainer
            className="Pipeline-editor__graph"
            lineContextId={`pipeline-lines-${pipelineId}`}
            lineElementCreators={lineElementCreators}
          >
            <div className="Pipeline-editor__bin">
              <Bin />
            </div>
            <div className="Pipeline-editor__elements">
              {Object.keys(elementStyles)
                .map(es => pipeline.merged.elements.add.find(e => e.id === es))
                .map(e => (
                  <div key={e.id} id={e.id} style={elementStyles[e.id]}>
                    <PipelineElement
                      pipelineId={pipelineId}
                      elementId={e.id}
                      onClick={() => setElementDetailsOpen(true)}
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
        </div>
        {isElementDetailsOpen ? (
          <ElementDetails
            pipelineId={pipelineId}
            className="Pipeline-editor__details"
            onClose={() => setElementDetailsOpen(false)}
          />
        ) : (
          <div />
        )}
      </PanelGroup>
    </div>
  );
};

PipelineEditor.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(PipelineEditor);
