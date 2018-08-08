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
import { Header, Loader } from 'semantic-ui-react';

import PanelGroup from 'react-panelgroup';

import DocRefBreadcrumb from 'components/DocRefBreadcrumb';
import SavePipeline from './SavePipeline';
import CreateChildPipeline from './CreateChildPipeline';
import OpenPipelineSettings from './OpenPipelineSettings';

import { LineContainer, LineTo } from 'components/LineTo';
import { mapObject } from 'lib/treeUtils';
import { getPipelineLayoutInformation } from './pipelineUtils';

import WithHeader from 'components/WithHeader';
import PipelineSettings from './PipelineSettings';
import PipelineElement from './PipelineElement';
import ElementPalette from './ElementPalette';
import DeletePipelineElement from './DeletePipelineElement';
import Bin from './Bin';

import lineElementCreators from './pipelineLineElementCreators';
import { ElementDetails } from './ElementDetails';

import { fetchPipeline, savePipeline } from './pipelineResourceClient';
import { fetchElements, fetchElementProperties } from './elementResourceClient';
import { withConfig } from 'startup/config';

const HORIZONTAL_SPACING = 150;
const VERTICAL_SPACING = 70;
const HORIZONTAL_START_PX = 10;
const VERTICAL_START_PX = 10;
const COMMON_ELEMENT_STYLE = {
  position: 'absolute',
};

const withElementDetailsOpen = withState('isElementDetailsOpen', 'setElementDetailsOpen', false);

const enhance = compose(
  withConfig,
  connect(
    (
      {
        docExplorer: {
          explorerTree: { documentTree },
        },
        pipelineEditor: { pipelines, elements },
      },
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
}) => (
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

const RawWithHeader = (props) => {
  const {
    pipelineId,
    pipeline: {
      pipeline: {
        docRef: { type },
        description,
      },
    }
  } = props;

  return (
    <WithHeader
      header={
        <Header as="h3">
          <img
            className="doc-ref__icon-large"
            alt="X"
            src={require(`../../images/docRefTypes/${type}.svg`)}
          />
          <Header.Content>
            <DocRefBreadcrumb docRefUuid={pipelineId}/>
            <Header.Subheader>{description}</Header.Subheader>
          </Header.Content>
        </Header>
      }
      actionBarItems={
        <React.Fragment>
          <SavePipeline {...props} />
          <CreateChildPipeline {...props} />
          <OpenPipelineSettings {...props} />
        </React.Fragment>
      }
      content={<RawPipelineEditor {...props} />}
    />
  );
};

const PipelineEditor = enhance(RawWithHeader);

PipelineEditor.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default PipelineEditor;
