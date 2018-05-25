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

import { canMove } from '../../lib/treeUtils';
import { ItemTypes } from './dragDropTypes';
import { DragSource, DropTarget } from 'react-dnd';

import { Icon } from 'semantic-ui-react'

import DocRef from './DocRef';

import FolderMenu from './FolderMenu';

import { withExistingExplorer } from './withExplorer';

import {
    moveExplorerItem,
    toggleFolderOpen,
    openDocRefContextMenu
} from './redux';

const dragSource = {
	canDrag(props) {
		return props.explorer.allowDragAndDrop;
	},
    beginDrag(props) {
        return {
            ...props.folder
        };
    }
};

function dragCollect(connect, monitor) {
    return {
        connectDragSource: connect.dragSource(),
        isDragging: monitor.isDragging()
    }
}

const dropTarget = {
    canDrop(props, monitor) {
        return props.explorer.allowDragAndDrop && canMove(monitor.getItem(), props.folder)
    },
    drop(props, monitor) {
        props.moveExplorerItem(props.explorerId, monitor.getItem(), props.folder);
    }
}

function dropCollect(connect, monitor) {
    return {
      connectDropTarget: connect.dropTarget(),
      isOver: monitor.isOver(),
      canDrop: monitor.canDrop()
    };
}

class Folder extends Component {
    static propTypes = {
        // props
        explorerId : PropTypes.string.isRequired,
        folder : PropTypes.object.isRequired,

        // state
        explorer : PropTypes.object.isRequired,

        // actions
        toggleFolderOpen : PropTypes.func.isRequired,
        moveExplorerItem : PropTypes.func.isRequired,
        openDocRefContextMenu : PropTypes.func.isRequired,

        // React DnD
        connectDropTarget: PropTypes.func.isRequired,
        isOver: PropTypes.bool.isRequired,
        connectDragSource: PropTypes.func.isRequired,
        isDragging: PropTypes.bool.isRequired
    }

    onRightClick(e) {
        this.props.openDocRefContextMenu(this.props.explorerId, this.props.folder);
        e.preventDefault();
    }

    renderChildren() {
        return this.props.folder.children
            .filter(c => !!this.props.explorer.isVisible[c.uuid])
            .map(c => (!!c.children) ?
                <DndFolder key={c.uuid} explorerId={this.props.explorerId} folder={c} /> :
                <DocRef key={c.uuid} explorerId={this.props.explorerId} docRef={c} />
            );
    }

    render() {
        const {
            connectDragSource,
            isDragging,
            connectDropTarget,
            isOver,
            canDrop,
            explorerId,
            explorer,
            folder,
            toggleFolderOpen
        } = this.props;
        let thisIsOpen = !!explorer.isFolderOpen[folder.uuid];
        let isContextMenuOpen = !!explorer.contextMenuItemUuid && explorer.contextMenuItemUuid === folder.uuid;
        let icon = thisIsOpen ? 'caret down' : 'caret right';
                
        let className = '';
        if (isOver) {
            className += ' folder__over';
        }
        if (isDragging) {
            className += ' folder__dragging '   
        }
        if (isOver) {
            if (canDrop) {
                className += ' folder__over_can_drop';
            } else {
                className += ' folder__over_cannot_drop';
            }
        }
        if (isContextMenuOpen) {
            className += ' doc-ref__context-menu-open'
        }

        return (
            <div>
                {connectDragSource(connectDropTarget(
                    <span className={className}
                            onContextMenu={this.onRightClick.bind(this)}
                            onClick={() => toggleFolderOpen(explorerId, folder)}>
                        <FolderMenu
                            explorerId={explorerId}
                            docRef={folder}
                            isOpen={isContextMenuOpen}
                        />
                        <span>
                            <Icon name={icon}/>
                            {folder.name}
                        </span>
                    </span>
                ))}
                {thisIsOpen && 
                    <div className='folder__children'>
                        {this.renderChildren()}
                    </div>
                }
            </div>
        )
    }
}

// We need to use this ourself, so create a variable
const DndFolder = connect(
    (state) => ({
        // state
    }),
    {
        moveExplorerItem,
        toggleFolderOpen,
        openDocRefContextMenu
    }
)(withExistingExplorer()(
    (DragSource(ItemTypes.FOLDER, dragSource, dragCollect)(
        DropTarget([ItemTypes.FOLDER, ItemTypes.DOC_REF], dropTarget, dropCollect)(
            Folder
        )
    ))));

export default DndFolder;