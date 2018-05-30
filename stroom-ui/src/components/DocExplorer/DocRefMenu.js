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
    requestDeleteDocRef,
    openDocRefContextMenu,
    closeDocRefContextMenu
} from './redux';

const DocRefMenu = ({
    explorerId,
    docRef,
    isOpen,
    openDocRef,
    requestDeleteDocRef,
    closeDocRefContextMenu
}) => {
    let onClose = () => {
        closeDocRefContextMenu(explorerId)
    }

    let onOpenDocRef = () => {
        openDocRef(explorerId, docRef);
        onClose();
    }

    let onRequestDeleteDocRef = () => {
        requestDeleteDocRef(explorerId, docRef);
        onClose();
    }

    return (
        <Dropdown inline icon={null}
            open={isOpen}
            onClose={onClose}
        >
            <Dropdown.Menu>
                <Dropdown.Item onClick={onOpenDocRef}>
                    <Icon name='file' />
                    Open
                </Dropdown.Item>
                <Dropdown.Item onClick={onRequestDeleteDocRef}>
                    <Icon name='trash' />
                    Delete
                </Dropdown.Item>
            </Dropdown.Menu>
        </Dropdown>
    )
}

DocRefMenu.propTypes = {
    explorerId : PropTypes.string.isRequired,
    docRef: PropTypes.object.isRequired,
    isOpen : PropTypes.bool.isRequired,

    openDocRef : PropTypes.func.isRequired,
    requestDeleteDocRef : PropTypes.func.isRequired,
    closeDocRefContextMenu : PropTypes.func.isRequired
};

export default connect(
    (state) => ({
        // state
    }),
    {
        openDocRef,
        requestDeleteDocRef,
        closeDocRefContextMenu
    }
)(DocRefMenu)