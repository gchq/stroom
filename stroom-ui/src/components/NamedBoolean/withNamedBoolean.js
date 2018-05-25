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
    namedBooleanCreated,
    namedBooleanDestroyed
} from './redux';

/**
 * This is a Higher Order Component
 * https://reactjs.org/docs/higher-order-components.html
 * 
 * It provides the state of a named boolean by using the given id property
 * to lookup the state in the namedBoolean redux reducer.
 * 
 * This allows react components that contain named boolean elements to remain stateless.
 * Such as modals, or toggles which radically alter the state of an element.
 * 
 * @param {React.Component} WrappedComponent 
 */
export function withNamedBoolean(WrappedComponent, idPropertyName, booleanPropertyName) {

    let WithNamedBoolean = class extends Component {
        static propTypes = {
            [idPropertyName]: PropTypes.string.isRequired,
            namedBoolean: PropTypes.object.isRequired
        }

        state = {
            [booleanPropertyName] : undefined
        }

        static getDerivedStateFromProps(nextProps, prevState) {
            let currentValue = nextProps.namedBoolean[nextProps[idPropertyName]];
            if (currentValue === undefined) {
                currentValue = false;
            }
            return {
                [booleanPropertyName] : currentValue
            }
        }

        componentDidMount() {
            this.props.namedBooleanCreated(this.props[idPropertyName]);
        }

        componentWillUnmount() {
            this.props.namedBooleanDestroyed(this.props[idPropertyName]);
        }

        render() {
            return <WrappedComponent {...this.state} {...this.props} />
        }
    }

    return connect(
        (state) => ({
            namedBoolean: state.namedBoolean
        }),
        {
            // actions
            namedBooleanCreated,
            namedBooleanDestroyed
        }
    )(WithNamedBoolean);
}
