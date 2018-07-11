import React from 'react';

import { compose } from 'recompose';
import { connect } from 'react-redux';
import { DropTarget } from 'react-dnd';
import { Button } from 'semantic-ui-react';

import ItemTypes from './dragDropTypes';
import { actionCreators } from './redux';

const { pipelineElementDeleteRequested } = actionCreators;

const dropTarget = {
  canDrop(props, monitor) {
    return true;
  },
  drop({ pipelineElementDeleteRequested }, monitor) {
    const { pipelineId, elementId } = monitor.getItem();
    pipelineElementDeleteRequested(pipelineId, elementId);
  },
};

const dropCollect = (connect, monitor) => ({
  connectDropTarget: connect.dropTarget(),
  isOver: monitor.isOver(),
  dndIsHappening: monitor.getItem() !== null,
});

const enhance = compose(
  connect((state, props) => ({}), { pipelineElementDeleteRequested }),
  DropTarget([ItemTypes.ELEMENT], dropTarget, dropCollect),
);

const Bin = ({ connectDropTarget, isOver, dndIsHappening }) =>
  connectDropTarget(<div>
    <Button
      circular
      disabled={!dndIsHappening}
      color={isOver ? 'black' : 'red'}
      size="huge"
      icon="trash"
    />
                    </div>);

export default enhance(Bin);
