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
import PropTypes from 'prop-types'

import { connect } from 'react-redux'

/**
 * This is a Higher Order Component
 * https://reactjs.org/docs/higher-order-components.html
 * 
 * It provides the pipeline by connecting to the redux store and using a provided
 * pipelineId to look it up. It also calculates the pipeline as a tree and provides that too.
 * The tree is used for putting the elements in the correct order.
 * 
 * @param {React.Component} WrappedComponent 
 */
export function withPipeline(WrappedComponent, customIdPropertyName) {
    let idPropertyName = customIdPropertyName || 'pipelineId';

    let WithPipeline = class extends Component {
        static propTypes = {
            [idPropertyName] : PropTypes.string.isRequired,
            pipelines : PropTypes.object.isRequired
        }

        state = {
            pipeline : undefined
        }
    
        static getDerivedStateFromProps(nextProps, prevState) {
            let pipeline = nextProps.pipelines[nextProps[idPropertyName]];

            return {
                pipeline
            }
        }

        render() {
            if (!!this.state.pipeline) {
                return <WrappedComponent
                    pipeline={this.state.pipeline} 
                    pipelineTree={this.state.pipelineTree} 
                    {...this.props}
                    />
            } else {
                return <div>awaiting pipeline state</div>
            }
        }
    }

    return connect(
        (state) => ({
            pipelines : state.pipelines
        }),
        {
            // actions
        }
    )(WithPipeline);
}