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

import { explorerTreeOpened, DEFAULT_EXPLORER_ID } from './redux';

/**
 * This is a Higher Order Component
 * https://reactjs.org/docs/higher-order-components.html
 * 
 * It provides the explorer by connecting to the redux store and using a provided
 * explorerId to look it up.
 */
export function withExistingExplorer(customIdPropertyName) {
    return WrappedComponent => {
        let idPropertyName = customIdPropertyName || 'explorerId';

        let WithExplorer = class extends Component {
            static propTypes = {
                [idPropertyName]: PropTypes.string.isRequired,
                explorers: PropTypes.object.isRequired
            }

            state = {
                explorer : undefined
            }

            static getDerivedStateFromProps(nextProps, prevState) {
                return {
                    explorer : nextProps.explorers[nextProps[idPropertyName]]
                }
            }

            render() {
                if (!!this.state.explorer) {
                    return <WrappedComponent 
                        {...this.state} 
                        {...this.props} 
                        />
                } else {
                    return <span>awaiting explorer state</span>
                }
            }
        }

        return connect(
            (state) => ({
                explorers: state.explorerTree.explorers
            }),
            {
                // actions
            }
        )(WithExplorer);
    }
}

/**
 * This higher order component is used to setup a new Doc Explorer.
 * It calls the explorerTreeOpened function on mount.
 */
export function withCreatedExplorer(customIdPropertyName) {
    return (WrappedComponent) => {
        let idPropertyName = customIdPropertyName || 'explorerId';

        // This component will want to retrieve the explorer
        let WrappedWithExplorer = withExistingExplorer(customIdPropertyName)(WrappedComponent);

        let WithCreatedExplorer = class extends Component {
            static propTypes = {
                allowMultiSelect: PropTypes.bool.isRequired,
                allowDragAndDrop: PropTypes.bool.isRequired,
                typeFilter: PropTypes.string,
            }

            static defaultProps = {
                explorerId: DEFAULT_EXPLORER_ID,
                [idPropertyName] : DEFAULT_EXPLORER_ID,
                allowMultiSelect: true,
                allowDragAndDrop: true,
                typeFilter: undefined,
            };
        
            componentDidMount() {
                // We give these properties to the explorer state, then the nested objects can read these values from
                // redux using the explorerId which is passed all the way down.
                this.props.explorerTreeOpened(
                    this.props[idPropertyName],
                    this.props.allowMultiSelect,
                    this.props.allowDragAndDrop,
                    this.props.typeFilter,
                );
            }

            render() {
                return <WrappedWithExplorer {...this.props} />
            }
        }

        return connect(
            (state) => ({
                // state
            }),
            {
                explorerTreeOpened
            }
        )(WithCreatedExplorer); 
    }
}