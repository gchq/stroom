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

import {
    Dropdown,
    Menu,
    Icon,
    Label,Input
} from 'semantic-ui-react'

import {
    requestDeletePipelineElement,
    closePipelineElementContextMenu
} from './redux';

const ElementMenu = ({
    pipelineId,
    elementId,
    isOpen,
    closePipelineElementContextMenu,
    requestDeletePipelineElement
}) => {
    return (
        <Dropdown
            floating 
            direction='right'
            icon={null}
            open={isOpen}
            onClose={() => closePipelineElementContextMenu(pipelineId)}
        >
            <Dropdown.Menu>
                <Dropdown.Item>
                    <Icon name='add' />         
                    <Dropdown simple item text='Add'>
                        <Dropdown.Menu>
                            <Dropdown.Item icon='edit' text='Edit Profile' />
                            <Dropdown.Item icon='globe' text='Choose Language' />
                            <Dropdown.Item icon='settings' text='Account Settings' />
                        </Dropdown.Menu>
                    </Dropdown>
                </Dropdown.Item>
                <Dropdown.Item onClick={() => requestDeletePipelineElement(pipelineId, elementId)}>
                    <Icon name='trash' />
                    Delete
                </Dropdown.Item>
            </Dropdown.Menu>
        </Dropdown>
    )
}

ElementMenu.propTypes = {
    pipelineId : PropTypes.string.isRequired,
    elementId: PropTypes.string.isRequired,
    isOpen : PropTypes.bool.isRequired,
    elements : PropTypes.object.isRequired,

    requestDeletePipelineElement : PropTypes.func.isRequired,
    closePipelineElementContextMenu : PropTypes.func.isRequired
}

export default connect(
    (state) => ({
        // state
        elements : state.elements.elements
    }),
    {
        closePipelineElementContextMenu,
        requestDeletePipelineElement
    }
)(ElementMenu)