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

import { connect } from 'react-redux'

import PipelineElement from './pipelineElement';

class PipelineEditor extends Component {
    static propTypes = {
        pipelineId : PropTypes.string.isRequired,
        pipelines : PropTypes.object.isRequired
    }

    state = {
        pipeline : undefined
    }

    static getDerivedStateFromProps(nextProps, prevState) {
        let pipeline = nextProps.pipelines[nextProps.pipelineId];

        return {
            pipeline
        }
    }

    render() {
        return (
            <div className='pipeline-editor'>
                <svg>
                    <text x="20" y="35">Pipeline Editor {this.props.pipelineId}</text>
                    <PipelineElement 
                        pipelineId={this.props.pipelineId}
                        elementId='test1'
                        />
                </svg>
                <div>
                    Pipeline Element Settings
                </div>
            </div>
        )
    }
}

export default connect(
    (state) => ({
        pipelines : state.pipelines
    }),
    {
        // actions
    }
)(PipelineEditor)