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
 * It provides the data source by connecting to the redux store and using a provided
 * dataSourceUuid to look it up.
 * 
 * @param {React.Component} WrappedComponent 
 */
export function withDataSource() {
    return WrappedComponent => {
        let WithDataSource = class extends Component {
            static propTypes = {
                dataSourceUuid: PropTypes.string.isRequired,
                dataSources: PropTypes.object.isRequired
            }

            state = {
                dataSource : undefined
            }
        
            static getDerivedStateFromProps(nextProps, prevState) {
                return {
                    dataSource : nextProps.dataSources[nextProps.dataSourceUuid]
                }
            }

            render() {
                if (!!this.state.dataSource) {
                    return <WrappedComponent dataSource={this.state.dataSource} {...this.props} />
                } else {
                    return <span>awaiting data source state</span>
                }
            }
        }

        return connect(
            (state) => ({
                dataSources : state.dataSources
            }),
            {
                // actions
            }
        )(WithDataSource);
    }
}