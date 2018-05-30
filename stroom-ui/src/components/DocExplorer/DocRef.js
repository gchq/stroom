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

import { compose } from 'redux';
import { connect } from 'react-redux'

import { ItemTypes } from './dragDropTypes';
import { DragSource } from 'react-dnd';

import { Icon } from 'semantic-ui-react'

import {
    selectDocRef,
    openDocRef,
    openDocRefContextMenu
} from './redux';

import { withExistingExplorer } from './withExplorer';

import DocRefMenu from './DocRefMenu';

const dragSource = {
	canDrag(props) {
		return props.explorer.allowDragAndDrop;
	},
    beginDrag(props) {
        return {
            ...props.docRef
        };
    }
};

function dragCollect(connect, monitor) {
    return {
        connectDragSource: connect.dragSource(),
        isDragging: monitor.isDragging()
    }
}

class DocRef extends Component {
    static propTypes = {
        // Props
        explorerId : PropTypes.string.isRequired,
        explorer : PropTypes.object.isRequired,
        docRef : PropTypes.object.isRequired,

        // Actions
        selectDocRef : PropTypes.func.isRequired,
        openDocRef : PropTypes.func.isRequired,
        openDocRefContextMenu : PropTypes.func.isRequired,

        // React DnD
        connectDragSource: PropTypes.func.isRequired,
        isDragging: PropTypes.bool.isRequired
    }
    
    // these are required to tell the difference between single/double clicks
    timer = 0;
    delay = 200;
    prevent = false;

    onSingleClick() {
        this.timer = setTimeout(function() {
            if (!this.prevent) {
                this.props.selectDocRef(this.props.explorerId, this.props.docRef); 
            }
            this.prevent = false;
        }.bind(this), this.delay);
    }

    onDoubleClick() {
        clearTimeout(this.timer);
        this.prevent = true;
        this.props.openDocRef(this.props.explorerId, this.props.docRef)
    }

    onRightClick(e) {
        this.props.openDocRefContextMenu(this.props.explorerId, this.props.docRef);
        e.preventDefault();
    }

    render() {
        const {
            connectDragSource,
            isDragging,
            explorerId,
            explorer,
            docRef
        } = this.props;

        let isSelected = explorer.isSelected[docRef.uuid];
        let isContextMenuOpen = !!explorer.contextMenuItemUuid && explorer.contextMenuItemUuid === docRef.uuid;

        let className = ''
        if (isDragging) {
            className += ' doc-ref__dragging'
        }
        if (isSelected) {
            className += ' doc-ref__selected'
        }
        if (isContextMenuOpen) {
            className += ' doc-ref__context-menu-open'
        }

        return connectDragSource(
            <div className={className}
                onContextMenu={this.onRightClick.bind(this)}
                onDoubleClick={this.onDoubleClick.bind(this)}
                onClick={this.onSingleClick.bind(this)}>
                <DocRefMenu 
                    explorerId={explorerId}
                    docRef={docRef}
                    isOpen={isContextMenuOpen}
                />
                <span>
                    <Icon name='file outline'/>
                    {docRef.name}
                </span>
            </div>
        )
    }
}

export default compose(
    connect(
        (state) => ({
            // state
        }),
        {
            selectDocRef,
            openDocRef,
            openDocRefContextMenu
        }
    ),
    withExistingExplorer(),
    DragSource(ItemTypes.DOC_REF, dragSource, dragCollect)
)(DocRef);