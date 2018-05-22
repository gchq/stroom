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

import { LineContainer, LineTo } from 'components/LineTo'

import { withPipeline } from './withPipeline'

import PipelineElement from './PipelineElement';

import './pipelineEditor.css';

import {
    Grid
} from 'semantic-ui-react';

class PipelineEditor extends Component {
    static propTypes = {
        pipelineId : PropTypes.string.isRequired,
        pipeline : PropTypes.object.isRequired
    }

    renderElements() {
        return this.props.pipeline.pipeline.elements.add.element.map(e => (
            <div key={e.id} id={e.id} className='Pipeline-element'>
                <PipelineElement 
                    pipelineId={this.props.pipelineId}
                    elementId={e.id}
                    />
            </div>
        ));
    }

    renderLines() {
        return this.props.pipeline.pipeline.links.add.link.map(l => {
            let lineId = l.from + '-' + l.to;
            return (
                <LineTo lineId={lineId} key={lineId} fromId={l.from} toId={l.to} />
            )
        });
    }

    render() {
        let {
            pipelineId,
            pipeline
        } = this.props;

        return (
            <Grid padded='vertically' divided='vertically'>
                <Grid.Row columns={1}>
                    <Grid.Column>
                        <LineContainer
                            lineContextId={'pipeline-lines-' + pipelineId}>
                            <h4>Pipeline Editor {pipelineId}</h4>
                            {this.renderElements()}
                            {this.renderLines()}
                        </LineContainer>
                        
                    </Grid.Column>
                </Grid.Row>
                <Grid.Row columns={1}>
                    <Grid.Column>
                        Pipeline Element Settings
                    </Grid.Column>
                </Grid.Row>
            </Grid>
        )
    }
}

export default withPipeline(PipelineEditor)