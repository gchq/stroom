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
import { Loader, Form, Button, Icon, Confirm } from 'semantic-ui-react';

import PanelGroup from 'react-panelgroup';

import { LineContainer, LineTo } from 'components/LineTo';
import { mapObject } from 'lib/treeUtils';
import { getPipelineLayoutInformation } from './pipelineUtils';

import PipelineElement from './PipelineElement';
import ElementPalette from './ElementPalette';
import Bin from './Bin';

import lineElementCreators from './pipelineLineElementCreators';
import { ElementDetails } from './ElementDetails';
import { DocPickerModal } from '../DocExplorer';

import { fetchPipeline, savePipeline } from './pipelineResourceClient';
import { fetchElements, fetchElementProperties } from './elementResourceClient';
import { withConfig } from 'startup/config';
import { actionCreators } from './redux';

const { pipelineElementDeleteCancelled, pipelineElementDeleted } = actionCreators;

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
      savePipeline,
      pipelineElementDeleteCancelled,
      pipelineElementDeleted,
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
    ({ pipeline }) => !pipeline.pipeline,
    renderComponent(() => <Loader active>Loading Pipeline Data</Loader>),
  ),
  branch(
    ({ elements }) => !elements.elements,
    renderComponent(() => <Loader active>Loading Elements</Loader>),
  ),
  withPaletteOpen,
  withElementDetailsOpen,
  withProps(({
    pipelineId,
    pipeline,
    setPaletteOpen,
    isPaletteOpen,
    pipelineElementDeleteCancelled,
    pipelineElementDeleted,
  }) => ({
    elementStyles: mapObject(getPipelineLayoutInformation(pipeline.asTree), (l) => {
      const index = l.verticalPos - 1;
      const fromTop = VERTICAL_START_PX + index * VERTICAL_SPACING;
      const fromLeft = HORIZONTAL_START_PX + l.horizontalPos * HORIZONTAL_SPACING;

      return {
        ...COMMON_ELEMENT_STYLE,
        top: `${fromTop}px`,
        left: `${fromLeft}px`,
      };
    }),
    isDirty: pipeline.isDirty,
    isSaving: pipeline.isSaving,
    pendingElementToDelete: pipeline.pendingElementToDelete,
    togglePaletteOpen: () => setPaletteOpen(!isPaletteOpen),
    onCancelDelete: () => pipelineElementDeleteCancelled(pipelineId),
    onConfirmDelete: () => {
      pipelineElementDeleted(pipelineId, pipeline.pendingElementToDelete);
    },
  })),
);

const PipelineEditor = ({
  pipelineId,
  pipeline,
  isPaletteOpen,
  togglePaletteOpen,
  isElementDetailsOpen,
  setElementDetailsOpen,
  editorClassName,
  elementStyles,
  savePipeline,
  isDirty,
  isSaving,
  pendingElementToDelete,
  onCancelDelete,
  onConfirmDelete,
}) => {
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
      <div className="Pipeline-editor__element-palette">
        <ElementPalette pipelineId={pipelineId} />
      </div>

      <button className="Pipeline-editor__palette-toggle" onClick={togglePaletteOpen}>
        {isPaletteOpen ? <Icon name="caret left" /> : <Icon name="caret right" />}
      </button>

      <PanelGroup direction="column" className="Pipeline-editor__content" panelWidths={panelSizes}>
        <div className="Pipeline-editor__topPanel">
          <div className="Pipeline-editor__top-bar">
            <div>
              <Button
                icon
                disabled={!isDirty}
                color="blue"
                size="huge"
                circular
                onClick={() => savePipeline(pipelineId)}
              >
                {isSaving ? <Loader size="small" active inline /> : <Icon name="save" />}
              </Button>
            </div>
            <Bin />
            <Form>
              <Form.Field>
                <label>Parent Pipeline</label>
                <DocPickerModal pickerId={pipelineId} typeFilter="Pipeline" />
              </Form.Field>
            </Form>
          </div>
          <LineContainer
            className="Pipeline-editor__graph"
            lineContextId={`pipeline-lines-${pipelineId}`}
            lineElementCreators={lineElementCreators}
          >
            <div className="Pipeline-editor__bin" />
            <div className="Pipeline-editor__elements">
              {Object.keys(elementStyles)
                .map(es => pipeline.pipeline.merged.elements.add.find(e => e.id === es))
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
              {pipeline.pipeline.merged.links.add
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
