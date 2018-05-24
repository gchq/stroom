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
import React, { Component } from 'react';
import PropTypes from 'prop-types';

import {
  LineContainer,
  LineTo
} from 'components/LineTo';

import { mapObject } from 'lib/treeUtils';

import { withPipeline } from './withPipeline';

import PipelineElement from './PipelineElement';
import PipelineElementSettings from './elements';

import './PipelineEditor.css';

class PipelineEditor extends Component {
  static propTypes = {
    pipelineId: PropTypes.string.isRequired,
    pipeline: PropTypes.object.isRequired,
    asTree : PropTypes.object.isRequired,
    layoutInformation : PropTypes.object.isRequired
  };

  // the state will hold the layout information, with layout information attached
  state = {
    elementStyles: {},
  };

  static getDerivedStateFromProps(nextProps, prevState) {
    const HORIZONTAL_SPACING = 150;
    const VERTICAL_SPACING = 100;
    const HORIZONTAL_START_PX = 50;
    const commonStyle = {
      position: 'absolute'
    };

    return {
      elementStyles : mapObject(nextProps.layoutInformation, l => ({
        ...commonStyle,
        top: `${l.verticalPos * VERTICAL_SPACING}px`,
        left: `${HORIZONTAL_START_PX + (l.horizontalPos * HORIZONTAL_SPACING)}px`,
      }))
    }
  }

  renderElements() {
    return this.props.pipeline.pipeline.elements.add.element.map(e => (
      <div key={e.id} id={e.id} style={this.state.elementStyles[e.id]}>
        <PipelineElement pipelineId={this.props.pipelineId} elementId={e.id} />
      </div>
    ));
  }

  renderLines() {
    return this.props.pipeline.pipeline.links.add.link
      .map(l => ({ ...l, lineId: `${l.from}-${l.to}` }))
      .map(l => <LineTo lineId={l.lineId} key={l.lineId} fromId={l.from} toId={l.to} />);
  }

  render() {
    const { pipelineId, pipeline } = this.props;

    return (
      <div className="Pipeline-editor">
        <LineContainer className='Pipeline-editor__overview' lineContextId={`pipeline-lines-${pipelineId}`}>
          <h4>Pipeline Editor {pipelineId}</h4>
          {this.renderElements()}
          {this.renderLines()}
        </LineContainer>

        <div className='Pipeline-editor__settings'>
          <PipelineElementSettings pipelineId={pipelineId} />
        </div>
      </div>
    );
  }
}

export default withPipeline(PipelineEditor);
