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
import { compose, lifecycle, withState, branch, renderComponent, withHandlers } from 'recompose';
import { withRouter } from 'react-router-dom';
import { connect } from 'react-redux';
import { Grid, Header } from 'semantic-ui-react';
import PanelGroup from 'react-panelgroup';

import Loader from 'components/Loader'
import AddElementModal from './AddElementModal';
import DocRefBreadcrumb from 'components/DocRefBreadcrumb';
import SavePipeline from './SavePipeline';
import CreateChildPipeline from './CreateChildPipeline';
import OpenPipelineSettings from './OpenPipelineSettings';
import DocRefImage from 'components/DocRefImage';
import PipelineSettings from './PipelineSettings';
import ElementPalette from './ElementPalette';
import DeletePipelineElement from './DeletePipelineElement';
import { ElementDetails } from './ElementDetails';
import { fetchPipeline, savePipeline } from './pipelineResourceClient';
import { actionCreators } from './redux';
import Pipeline from './Pipeline';

const {
  startInheritedPipeline,
  pipelineSettingsOpened
} = actionCreators;

const withElementDetailsOpen = withState('isElementDetailsOpen', 'setElementDetailsOpen', false);

const enhance = compose(
  withRouter,
  withHandlers({
    openDocRef: ({ history }) => d => history.push(`/s/doc/${d.type}/${d.uuid}`)
  }),
  connect(
    (
      { pipelineEditor: { pipelineStates } },
      { pipelineId },
    ) => ({
      pipelineState: pipelineStates[pipelineId],
    }),
    {
      // action, needed by lifecycle hook below
      fetchPipeline,
      savePipeline,
      startInheritedPipeline,
      pipelineSettingsOpened
    },
  ),
  lifecycle({
    componentDidMount() {
      const { fetchPipeline, pipelineId } = this.props;
      fetchPipeline(pipelineId);
    },
  }),
  branch(
    ({ pipelineState }) => !(pipelineState && pipelineState.pipeline),
    renderComponent(() => <Loader message="Loading pipeline..." />),
  ),
  withElementDetailsOpen,
);

const RawPipelineEditor = ({
  pipelineId,
  pipelineState: { pipeline },
  isElementDetailsOpen,
  setElementDetailsOpen,
  openDocRef,
  savePipeline,
  startInheritedPipeline,
  pipelineSettingsOpened
}) => (
    <React.Fragment>
      <Grid className="content-tabs__grid">
        <Grid.Column width={12}><Header as="h3">
          <DocRefImage docRefType={pipeline.docRef.type} />
          <Header.Content>{pipeline.docRef.name}</Header.Content>
          <Header.Subheader>
            <DocRefBreadcrumb docRefUuid={pipelineId} openDocRef={openDocRef} />
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
        <AddElementModal pipelineId={pipelineId} />
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
            <Pipeline
              pipelineId={pipelineId}
              onElementSelected={() => setElementDetailsOpen(true)} />
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
