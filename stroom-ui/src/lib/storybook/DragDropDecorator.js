
import React, { Component } from 'react';
import { DragDropContext } from 'react-dnd';
import HTML5Backend from 'react-dnd-html5-backend';

class WrappedComponent extends Component {
    render() {
        return this.props.children;
    }
}

let DragDropComponent = DragDropContext(HTML5Backend)(WrappedComponent)

export const DragDropDecorator = (storyFn) => {
    return (
        <DragDropComponent>
            {storyFn()}
        </DragDropComponent>
    )
}
