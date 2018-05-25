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

import {
    modalCreated,
    modalDestroyed
} from './redux';

/**
 * This is a Higher Order Component
 * https://reactjs.org/docs/higher-order-components.html
 * 
 * It provides the state of the modal (open/closed) by using the given modalId
 * (or custom named ID property) to lookup the state in the modal redux reducer.
 * 
 * This allows react components that contain modal elements to remain stateless.
 * 
 * @param {React.Component} WrappedComponent 
 */
export function withModal(WrappedComponent, customIdPropertyName) {
    let idPropertyName = customIdPropertyName || 'modalId';

    let WithModal = class extends Component {
        static propTypes = {
            [idPropertyName]: PropTypes.string.isRequired,
            modal: PropTypes.object.isRequired
        }

        state = {
            modalOpen : undefined
        }

        static getDerivedStateFromProps(nextProps, prevState) {
            let currentValue = nextProps.modal[nextProps[idPropertyName]];
            if (currentValue === undefined) {
                currentValue = false;
            }
            return {
                modalOpen : currentValue
            }
        }

        render() {
            return <WrappedComponent modalOpen={this.state.modalOpen} {...this.props} />
        }
    }

    return connect(
        (state) => ({
            modal: state.modal
        }),
        {
            // actions
        }
    )(WithModal);
}
