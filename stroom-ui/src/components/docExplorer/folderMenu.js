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
} from './redux/explorerTreeReducer';

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