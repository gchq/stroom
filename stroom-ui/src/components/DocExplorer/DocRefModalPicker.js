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
    Button,
    Header,
    Icon,
    Modal,
    Input
} from 'semantic-ui-react'

import { findItem } from 'lib/treeUtils';
import { docRefPicked } from './redux';
import { withCreatedExplorer } from './withExplorer';
import { withPickedDocRef } from './withPickedDocRef';
import {
    withModal,
    setModal
} from 'components/WithModal';

import DocExplorer from './DocExplorer';

const DocRefModalPicker = (props) => {
    let {
        isSelected,
        documentTree,
        docRefPicked,
        docRef,
        modalIsOpen,
        tree,
        pickerId,
        typeFilter,
        setModal,
        explorer
    } = props;

    let value = (!!docRef) ? docRef.name : '';

    let handleOpen = () => setModal(pickerId, true);

    let handleClose = () => setModal(pickerId, false);
        
    let onDocRefSelected = () => {
        Object.keys(explorer.isSelected)
        .forEach(pickedUuid => {
            let picked = findItem(documentTree, pickedUuid);
            docRefPicked(pickerId, picked);
        });
    
        handleClose();
    }

    return (
        <Modal
            trigger={<Input onFocus={handleOpen} value={value + '...'}/>}
            open={modalIsOpen}
            onClose={handleClose}
            size='small'
            dimmer='blurring'
        >
            <Modal.Header>Select a Doc Ref</Modal.Header>
            <Modal.Content scrolling>
                <DocExplorer tree={tree}
                            explorerId={pickerId}
                            allowMultiSelect={false}
                            allowDragAndDrop={false}
                            typeFilter={typeFilter}
                            />
            </Modal.Content>
            <Modal.Actions>
                <Button negative onClick={() => setModal(pickerId, false)}>Cancel</Button>
                <Button positive onClick={onDocRefSelected} labelPosition='right' icon='checkmark' content='Choose' />
            </Modal.Actions>
        </Modal>
    )
}

DocRefModalPicker.propTypes = {
    pickerId : PropTypes.string.isRequired,
    documentTree : PropTypes.object.isRequired,
    explorer : PropTypes.object.isRequired,

    typeFilter : PropTypes.string,
    docRef : PropTypes.object,
    docRefPicked : PropTypes.func.isRequired,
    modalIsOpen : PropTypes.bool.isRequired,

    setModal : PropTypes.func.isRequired
}

export default connect(
    (state) => ({
        documentTree : state.explorerTree.documentTree
    }),
    {
        // actions
        docRefPicked,
        setModal
    }
)(withPickedDocRef()(withCreatedExplorer('pickerId')(withModal('pickerId')(DocRefModalPicker))));
