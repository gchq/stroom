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

import {
    pickDocRef
} from './redux';

import DocExplorer from './DocExplorer';

class DocRefModalPicker extends Component {
    static propTypes = {
        pickerId : PropTypes.string.isRequired,
        documentTree : PropTypes.object.isRequired,
        explorers : PropTypes.object.isRequired,

        typeFilter : PropTypes.string,
        value : PropTypes.object,
        onChange : PropTypes.func.isRequired
    }

    state = {
        modalOpen : false
    }

    handleOpen() {
        this.setState({ modalOpen: true });
    }

    handleClose() {
        this.setState({ modalOpen: false });
    }

    onDocRefSelected() {
        Object.keys(this.props.explorers[this.props.pickerId].isSelected)
            .forEach(pickedUuid => {
                let picked = findItem(this.props.documentTree, pickedUuid);
                this.props.onChange(picked);
            });
        this.handleClose();
    }

    render() {
        let value = (!!this.props.value) ? this.props.value.name : '';

        return (
            <Modal
                trigger={<Input onFocus={this.handleOpen.bind(this)} value={value + '...'}/>}
                open={this.state.modalOpen}
                onClose={this.handleClose}
                size='small'
                dimmer='blurring'
            >
                <Modal.Header>Select a Doc Ref</Modal.Header>
                <Modal.Content scrolling>
                    <DocExplorer tree={this.props.tree}
                                explorerId={this.props.pickerId}
                                allowMultiSelect={false}
                                allowDragAndDrop={false}
                                typeFilter={this.props.typeFilter}
                                />
                </Modal.Content>
                <Modal.Actions>
                    <Button negative onClick={this.handleClose.bind(this)}>Cancel</Button>
                    <Button positive onClick={this.onDocRefSelected.bind(this)} labelPosition='right' icon='checkmark' content='Choose' />
                </Modal.Actions>
            </Modal>
        )
    }
}

export default connect(
    (state) => ({
        documentTree : state.explorerTree.documentTree,
        explorers : state.explorerTree.explorers
    }),
    {
        // actions
    }
)(DocRefModalPicker);
