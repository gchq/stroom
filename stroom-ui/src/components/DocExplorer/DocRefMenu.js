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
    openDocRef,
    deleteDocRef,
    openDocRefContextMenu,
    closeDocRefContextMenu
} from './redux';

class DocRefMenu extends Component {
    static propTypes = {
        explorerId : PropTypes.string.isRequired,
        docRef: PropTypes.object.isRequired,
        isOpen : PropTypes.bool.isRequired,

        openDocRef : PropTypes.func.isRequired,
        deleteDocRef : PropTypes.func.isRequired,
        closeDocRefContextMenu : PropTypes.func.isRequired
    }

    onOpenDocRef() {
        this.props.openDocRef(this.props.explorerId, this.props.docRef);
        this.onClose();
    }

    onDeleteDocRef() {
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
                    <Dropdown.Item onClick={this.onOpenDocRef.bind(this)}>
                        <Icon name='file' />
                        Open
                    </Dropdown.Item>
                    <Dropdown.Item onClick={this.onDeleteDocRef.bind(this)}>
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
        openDocRef,
        deleteDocRef,
        closeDocRefContextMenu
    }
)(DocRefMenu)