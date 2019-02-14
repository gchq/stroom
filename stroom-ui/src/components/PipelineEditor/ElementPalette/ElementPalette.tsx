import * as React from "react";

import { DropTarget, DropTargetSpec, DropTargetCollector } from "react-dnd";
import { compose, withProps } from "recompose";
import { connect } from "react-redux";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

import ElementCategory from "./ElementCategory";
import { getBinItems, RecycleBinItem } from "../pipelineUtils";
import { DragDropTypes, DropCollectedProps } from "../dragDropTypes";
import { GlobalStoreState } from "../../../startup/reducers";
import {
  ElementDefinitionsByCategory,
  ElementDefinition
} from "../../../types";

export interface Props {
  pipelineId: string;
  showDeleteElementDialog: (elementId: string) => void;
}

interface ConnectState {
  byCategory: ElementDefinitionsByCategory;
  recycleBinItems: Array<RecycleBinItem>;
}
interface ConnectDispatch {}

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
  drop({ showDeleteElementDialog }, monitor) {
    const { elementId } = monitor.getItem();
    showDeleteElementDialog(elementId);
  }
};

const dropCollect: DropTargetCollector<DropCollectedProps> = (
  connect,
  monitor
) => ({
  connectDropTarget: connect.dropTarget(),
  isOver: monitor.isOver(),
  draggingItemType: monitor.getItemType(),
  canDrop: monitor.canDrop()
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
  draggingItemType
}: EnhancedProps) =>
  connectDropTarget(
    <div className="element-palette">
      {draggingItemType === DragDropTypes.ELEMENT ? (
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
