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
import { withRouter } from 'react-router-dom';
import { connect } from 'react-redux';
import { Grid, Header, Loader } from 'semantic-ui-react';

import PanelGroup from 'react-panelgroup';

import DocRefBreadcrumb from 'components/DocRefBreadcrumb';
import SavePipeline from './SavePipeline';
import CreateChildPipeline from './CreateChildPipeline';
import OpenPipelineSettings from './OpenPipelineSettings';

import { LineContainer, LineTo } from 'components/LineTo';
import { mapObject } from 'lib/treeUtils';
import { getPipelineLayoutInformation } from './pipelineUtils';

import PipelineSettings from './PipelineSettings';
import PipelineElement from './PipelineElement';
import ElementPalette from './ElementPalette';
import DeletePipelineElement from './DeletePipelineElement';
import Bin from './Bin';

import lineElementCreators from './pipelineLineElementCreators';
import { ElementDetails } from './ElementDetails';

import { openDocRef } from 'sections/RecentItems';
import { fetchPipeline, savePipeline } from './pipelineResourceClient';
import { fetchElements, fetchElementProperties } from './elementResourceClient';
import { actionCreators } from './redux';

const {
  startInheritedPipeline,
  pipelineSettingsOpened
} = actionCreators;

const HORIZONTAL_SPACING = 150;
const VERTICAL_SPACING = 70;
const HORIZONTAL_START_PX = 10;
const VERTICAL_START_PX = 10;
const COMMON_ELEMENT_STYLE = {
  position: 'absolute',
};

const withElementDetailsOpen = withState('isElementDetailsOpen', 'setElementDetailsOpen', false);

const enhance = compose(
  withRouter,
  connect(
    (
      { pipelineEditor: { pipelines, elements } },
      { pipelineId },
    ) => ({
      pipeline: pipelines[pipelineId],
      elements,
    }),
    {
      // action, needed by lifecycle hook below
      fetchPipeline,
      savePipeline,
      fetchElements,
      fetchElementProperties,
      openDocRef,
      startInheritedPipeline,
      pipelineSettingsOpened
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
  withElementDetailsOpen,
  withProps(({ pipelineId, pipeline: { asTree } }) => ({
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
  })),
);

const RawPipelineEditor = ({
  pipelineId,
  pipeline: { pipeline, isDirty, isSaving },
  isElementDetailsOpen,
  setElementDetailsOpen,
  editorClassName,
  elementStyles,
  history,
  openDocRef,
  savePipeline,
  startInheritedPipeline,
  pipelineSettingsOpened
}) => (
  <React.Fragment>
    <Grid className="content-tabs__grid">
      <Grid.Column width={12}><Header as="h3">
        <img
          className="stroom-icon--large"
          alt="X"
          src={require(`../../images/docRefTypes/${pipeline.docRef.type}.svg`)}
        />
        <Header.Content>{pipeline.docRef.name}</Header.Content>
        <Header.Subheader>
          <DocRefBreadcrumb docRefUuid={pipelineId} openDocRef={l => openDocRef(history, l)} />
        </Header.Subheader>
      </Header></Grid.Column>
      <Grid.Column width={4}>
        <SavePipeline pipelineId={pipelineId} pipeline={pipeline} savePipeline={savePipeline} />
        <CreateChildPipeline
          pipelineId={pipelineId}
          startInheritedPipeline={startInheritedPipeline}
        />
        <OpenPipelineSettings
          pipelineId={pipelineId}
          pipelineSettingsOpened={pipelineSettingsOpened}
        /></Grid.Column>
    </Grid>
    <div className="Pipeline-editor">
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
            resize: 'dynamic',
            size: isElementDetailsOpen ? '50%' : 0,
          },
        ]}
      >
        <div className="Pipeline-editor__topPanel">
          <LineContainer
            className="Pipeline-editor__graph background-element"
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
  </React.Fragment>

);

const PipelineEditor = enhance(RawPipelineEditor);

PipelineEditor.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default PipelineEditor;
