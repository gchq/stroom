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
    Icon
} from 'semantic-ui-react'

import {
    toggleFolderOpen,
    deleteDocRef,
    closeDocRefContextMenu
} from './redux';

class FolderMenu extends Component {
    static propTypes = {
        explorerId : PropTypes.string.isRequired,
        docRef: PropTypes.object.isRequired,
        isOpen : PropTypes.bool.isRequired,

        toggleFolderOpen : PropTypes.func.isRequired,
        deleteDocRef : PropTypes.func.isRequired,
        closeDocRefContextMenu : PropTypes.func.isRequired
    }

    onOpenFolder() {
        this.props.toggleFolderOpen(this.props.explorerId, this.props.docRef);
        this.onClose();
    }

    onDeleteFolder() {
        this.props.deleteDocRef(this.props.explorerId, this.props.docRef);
        this.onClose();
    }

    onClose() {
        this.props.closeDocRefContextMenu(this.props.explorerId)
    }

    render() {

        return (
            <Dropdown inline icon={null}
                open={this.props.isOpen}
                onClose={this.onClose.bind(this)}
            >
                <Dropdown.Menu>
                <Dropdown.Item onClick={this.onOpenFolder.bind(this)}>
                    <Icon name='folder' />
                    Open
                </Dropdown.Item>
                <Dropdown.Item onClick={this.onDeleteFolder.bind(this)}>
                    <Icon name='trash' />
                    Delete
                </Dropdown.Item>
                </Dropdown.Menu>
            </Dropdown>
        )
    }
}

export default connect(
    (state) => ({
        // state
    }),
    {
        toggleFolderOpen,
        deleteDocRef,
        closeDocRefContextMenu
    }
)(FolderMenu)