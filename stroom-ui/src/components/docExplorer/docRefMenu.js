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
} from './redux/explorerTreeReducer';

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