import React, { Component } from 'react';
import PropTypes from 'prop-types';

import { action } from '@storybook/addon-actions';

/**
 * An abstract component that expects to house a picker.
 * It will have a pickerId, an initial value (from the story)
 * and a name for the action to trigger when changes are received.
 */
class ManagedPickerHarness extends Component {
    static propTypes = {
        pickerId : PropTypes.string.isRequired,
        initialValue : PropTypes.object,
        actionName : PropTypes.string.isRequired
    }

    constructor(props) {
        super(props);

        this.state = {
            value : props.initialValue
        }

        this.action = action(props.actionName);
    }

    onChange(value) {
        this.action(value);
        this.setState({
            value
        })
    }
}

export default ManagedPickerHarness;