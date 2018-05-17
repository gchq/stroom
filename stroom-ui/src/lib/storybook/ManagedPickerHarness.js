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