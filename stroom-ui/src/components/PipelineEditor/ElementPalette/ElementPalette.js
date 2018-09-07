import React from 'react';
import PropTypes from 'prop-types';

import { DropTarget } from 'react-dnd';
import { compose, withProps } from 'recompose';
import { connect } from 'react-redux';
import { Icon } from 'semantic-ui-react';

import ElementCategory from './ElementCategory';
import { getBinItems } from '../pipelineUtils';
import ItemTypes from '../dragDropTypes';
import { actionCreators } from '../redux';

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
  connect(
    (
      {
        pipelineEditor: {
          pipelineStates,
          elements: { byCategory = {}, byType = {} },
        },
      },
      { pipelineId },
    ) => {
      const pipelineState = pipelineStates[pipelineId];

      return {
        byCategory,
        recycleBinItems: pipelineState ? getBinItems(pipelineState.pipeline, byType) : [],
      };
    },
    {
      pipelineElementDeleteRequested,
    },
  ),
  DropTarget([ItemTypes.ELEMENT], dropTarget, dropCollect),
  withProps(({ isOver }) => ({
    binColour: isOver ? 'red' : 'black',
  })),
);

const ElementPalette = ({
  byCategory,
  recycleBinItems,
  binColour,
  connectDropTarget,
  dndIsHappening,
}) =>
  connectDropTarget(<div className="element-palette">
    {dndIsHappening ? (
      <div className="Pipeline-editor__bin">
        <Icon name="trash" size="huge" color={binColour} />
      </div>
      ) : (
        <React.Fragment>
          <ElementCategory category="Bin" elementsWithData={recycleBinItems} />
          {Object.entries(byCategory).map(k => (
            <ElementCategory
              key={k[0]}
              category={k[0]}
              elementsWithData={k[1].map(e => ({ element: e }))}
            />
          ))}
        </React.Fragment>
      )}
                    </div>);

ElementPalette.propTypes = {
  pipelineId: PropTypes.string.isRequired,
};

export default enhance(ElementPalette);
