import * as React from "react";

import { DropTarget, DropTargetSpec, DropTargetCollector } from "react-dnd";
import { compose, withProps } from "recompose";
import { connect } from "react-redux";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

import ElementCategory from "./ElementCategory";
import { getBinItems, RecycleBinItem } from "../pipelineUtils";
import { DragDropTypes, DropCollectedProps } from "../dragDropTypes";
import { actionCreators } from "../redux";
import { GlobalStoreState } from "../../../startup/reducers";
import {
  ElementDefinitionsByCategory,
  ElementDefinition
} from "../../../types";

const { pipelineElementDeleteRequested } = actionCreators;

export interface Props {
  pipelineId: string;
}

interface ConnectState {
  byCategory: ElementDefinitionsByCategory;
  recycleBinItems: Array<RecycleBinItem>;
}
interface ConnectDispatch {
  pipelineElementDeleteRequested: typeof pipelineElementDeleteRequested;
}

export interface DndProps extends Props, ConnectDispatch, ConnectState {}

interface WithProps {
  binColour: string;
}

export interface EnhancedProps
  extends Props,
    ConnectDispatch,
    ConnectState,
    DropCollectedProps,
    WithProps {}

const dropTarget: DropTargetSpec<DndProps> = {
  canDrop(props, monitor) {
    return true;
  },
  drop({ pipelineElementDeleteRequested }, monitor) {
    const { pipelineId, elementId } = monitor.getItem();
    pipelineElementDeleteRequested(pipelineId, elementId);
  }
};

const dropCollect: DropTargetCollector<DropCollectedProps> = (
  connect,
  monitor
) => ({
  connectDropTarget: connect.dropTarget(),
  isOver: monitor.isOver(),
  dndIsHappening: monitor.getItem() !== null
});

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    (
      {
        pipelineEditor: {
          pipelineStates,
          elements: { byCategory = {}, byType = {} }
        }
      },
      { pipelineId }
    ) => {
      const pipelineState = pipelineStates[pipelineId];

      return {
        byCategory,
        recycleBinItems:
          pipelineState && pipelineState.pipeline
            ? getBinItems(pipelineState.pipeline, byType)
            : []
      };
    },
    {
      pipelineElementDeleteRequested
    }
  ),
  DropTarget([DragDropTypes.ELEMENT], dropTarget, dropCollect),
  withProps(({ isOver }) => ({
    binColour: isOver ? "red" : "black"
  }))
);

const ElementPalette = ({
  byCategory,
  recycleBinItems,
  binColour,
  connectDropTarget,
  dndIsHappening
}: EnhancedProps) =>
  connectDropTarget(
    <div className="element-palette">
      {dndIsHappening ? (
        <div className="Pipeline-editor__bin">
          <FontAwesomeIcon icon="trash" size="lg" color={binColour} />
        </div>
      ) : (
        <React.Fragment>
          <ElementCategory category="Bin" elementsWithData={recycleBinItems} />
          {Object.entries(byCategory).map(k => (
            <ElementCategory
              key={k[0]}
              category={k[0]}
              elementsWithData={k[1].map((e: ElementDefinition) => ({
                element: e
              }))}
            />
          ))}
        </React.Fragment>
      )}
    </div>
  );

export default enhance(ElementPalette);
