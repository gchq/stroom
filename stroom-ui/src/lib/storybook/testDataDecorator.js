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