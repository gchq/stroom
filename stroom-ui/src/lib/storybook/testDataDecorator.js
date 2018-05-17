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

/**
 * A decorator component that fires its initializeFunction action with its initialize data
 */
class TestInitialisationDecorator extends Component {
    static propTypes = {
        initializeData : PropTypes.object,
        initializeFunction : PropTypes.func.isRequired
    }

    componentDidMount() {
        this.props.initializeFunction(this.props.initializeData);
    }

    render() {
        return this.props.children;
    }
}

const testInitialisationDecorator = (initializeFunction, initializeData) => {
    let Connected = connect(
        (state) => ({}),
        {
            initializeFunction : initializeFunction
        }
    )(TestInitialisationDecorator);

    return (storyFn) => (
        <Connected initializeData={initializeData}>
            {storyFn()}
        </Connected>
    )
}

/**
 * A decorator component that fires receiveDocTree actions for the given explorer tree
 */
class TestMultiInitialisationDecorator extends Component {
    static propTypes = {
        initializeData : PropTypes.object,
        initializeFunction : PropTypes.func.isRequired
    }

    componentDidMount() {
        Object.entries(this.props.initializeData).forEach(k => {
            this.props.initializeFunction(k[0], k[1]);
        });
    }

    render() {
        return this.props.children;
    }
}

const testMultiInitialisationDecorator = (initializeFunction, initializeData) => {
    let Connected = connect(
        (state) => ({}),
        {
            initializeFunction : initializeFunction
        }
    )(TestMultiInitialisationDecorator);

    return (storyFn) => (
        <Connected initializeData={initializeData}>
            {storyFn()}
        </Connected>
    )
}

export {
    testInitialisationDecorator,
    testMultiInitialisationDecorator
};