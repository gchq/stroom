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
import * as React from "react";
import { compose, withProps, withHandlers } from "recompose";
import { connect } from "react-redux";
import {
  DragSource,
  DropTarget,
  DragSourceSpec,
  DragSourceCollector,
  DropTargetSpec,
  DropTargetCollector
} from "react-dnd";

import { StoreStateById as PipelineStateStoreById } from "./redux/pipelineStatesReducer";
import { StoreState as ElementsStoreState } from "./redux/elementReducer";
import ElementImage from "../ElementImage";
import { actionCreators } from "./redux";
import { canMovePipelineElement, getInitialValues } from "./pipelineUtils";
import {
  DragDropTypes,
  DragCollectedProps,
  DropCollectedProps
} from "./dragDropTypes";
import { isValidChildType } from "./elementUtils";
import Button from "../Button";
import {
  ElementDefinition,
  PipelineElementType,
  PipelinePropertyType
} from "../../types";
import { GlobalStoreState } from "../../startup/reducers";

const {
  pipelineElementAddRequested,
  pipelineElementSelected,
  pipelineElementMoved,
  pipelineElementReinstated
} = actionCreators;

export interface Props {
  pipelineId: string;
  elementId: string;
  className?: string;
}

interface ConnectState {
  pipelineState: PipelineStateStoreById;
  selectedElementId?: string;
  element?: PipelineElementType;
  elementDefinition?: ElementDefinition;
  elements?: ElementsStoreState;
}

interface ConnectDispatch {
  pipelineElementAddRequested: typeof pipelineElementAddRequested;
  pipelineElementSelected: typeof pipelineElementSelected;
  pipelineElementMoved: typeof pipelineElementMoved;
  pipelineElementReinstated: typeof pipelineElementReinstated;
}

interface DndProps extends Props, ConnectState, ConnectDispatch {}

interface DragObject {
  pipelineId: string;
  elementId: string;
  elementDefinition: ElementDefinition;
}

interface WithHandlers {
  onElementClick: React.MouseEventHandler<HTMLDivElement>;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    DropCollectedProps,
    DragCollectedProps,
    WithHandlers {}

const dragSource: DragSourceSpec<DndProps, DragObject> = {
  canDrag(props) {
    return true;
  },
  beginDrag(props) {
    return {
      pipelineId: props.pipelineId,
      elementId: props.elementId,
      elementDefinition: props.elementDefinition!
    };
  }
};

const dragCollect: DragSourceCollector<DragCollectedProps> = (
  connect,
  monitor
) => ({
  connectDragSource: connect.dragSource(),
  isDragging: monitor.isDragging()
});

const dropTarget: DropTargetSpec<DndProps> = {
  canDrop(props, monitor) {
    const { pipelineState, elementId, elementDefinition } = props;

    switch (monitor.getItemType()) {
      case DragDropTypes.ELEMENT:
        const dropeeId = monitor.getItem().elementId;
        const dropeeDefinition = monitor.getItem().elementDefinition;
        const isValidChild = isValidChildType(
          elementDefinition!,
          dropeeDefinition,
          0
        );

        const isValid = canMovePipelineElement(
          pipelineState.pipeline!,
          pipelineState.asTree!,
          dropeeId,
          elementId
        );

        return isValidChild && isValid;
      case DragDropTypes.PALLETE_ELEMENT:
        const dropeeType = monitor.getItem().element;
        if (dropeeType) {
          const isValidChild = isValidChildType(
            elementDefinition!,
            dropeeType,
            0
          );
          return isValidChild;
        }
        return true;

      default:
        return false;
    }
  },
  drop(props, monitor) {
    const {
      elementId,
      pipelineId,
      pipelineElementMoved,
      pipelineElementAddRequested,
      pipelineElementReinstated
    } = props;

    switch (monitor.getItemType()) {
      case DragDropTypes.ELEMENT: {
        const newElementId = monitor.getItem().elementId;
        pipelineElementMoved(pipelineId, newElementId, elementId);
        break;
      }
      case DragDropTypes.PALLETE_ELEMENT: {
        const { element, recycleData } = monitor.getItem();

        if (recycleData) {
          pipelineElementReinstated(pipelineId, elementId, recycleData);
        } else {
          pipelineElementAddRequested(pipelineId, elementId, element);
        }
        break;
      }
      default:
        break;
    }
  }
};

const dropCollect: DropTargetCollector<DropCollectedProps> = (
  connect,
  monitor
) => ({
  connectDropTarget: connect.dropTarget(),
  isOver: monitor.isOver(),
  canDrop: monitor.canDrop(),
  draggingItemType: monitor.getItemType()
});

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    (
      { pipelineEditor: { pipelineStates, elements } },
      { pipelineId, elementId }
    ) => {
      const pipelineState = pipelineStates[pipelineId];

      let selectedElementId;
      let element: PipelineElementType | undefined;
      let elementDefinition: ElementDefinition | undefined;

      if (pipelineState && pipelineState.pipeline) {
        selectedElementId = pipelineState.selectedElementId;
        element =
          pipelineState.pipeline.merged.elements.add &&
          pipelineState.pipeline.merged.elements.add.find(
            (e: PipelineElementType) => e.id === elementId
          );
        if (element) {
          elementDefinition = Object.values(elements.elements).find(
            e => e.type === element!.type
          )!;
        }
      }

      return {
        // state
        pipelineState,
        selectedElementId,
        element,
        elementDefinition,
        elements
      };
    },
    {
      // actions
      pipelineElementSelected,
      pipelineElementAddRequested,
      pipelineElementMoved,
      pipelineElementReinstated
    }
  ),
  DragSource(DragDropTypes.ELEMENT, dragSource, dragCollect),
  DropTarget(
    [DragDropTypes.ELEMENT, DragDropTypes.PALLETE_ELEMENT],
    dropTarget,
    dropCollect
  ),
  withProps(
    ({
      isOver,
      canDrop,
      isDragging,
      selectedElementId,
      elementId,
      draggingItemType
    }) => {
      const classNames = ["Pipeline-element"];
      classNames.push("raised-low");
      let isIconDisabled = false;

      if (!!draggingItemType) {
        if (isOver) {
          classNames.push("over");
        }
        if (isDragging) {
          classNames.push("dragging");
        }

        if (canDrop) {
          classNames.push("can_drop");
        } else {
          isIconDisabled = true;
          classNames.push("cannot_drop");
        }

        if (selectedElementId === elementId) {
          classNames.push("selected");
        }
      } else {
        classNames.push("borderless");
      }

      return {
        className: classNames.join(" "),
        isIconDisabled
      };
    }
  ),
  withHandlers({
    onElementClick: ({
      element,
      elements: { elementProperties },
      pipelineState: { pipeline },
      pipelineElementSelected,
      pipelineId,
      elementId
    }) => () => {
      // We need to get the initial values for this element and make sure they go into the state,
      // ready for redux-form to populate the new form.
      // TODO THIS MUST SURELY BE CHANGED NOW WE ARE USING FORMIK
      const thisElementTypeProperties = elementProperties[element.type];
      const thisElementProperties = pipeline.merged.properties.add.filter(
        (property: PipelinePropertyType) => property.element === element.id
      );
      const initialValues = getInitialValues(
        thisElementTypeProperties,
        thisElementProperties
      );
      return pipelineElementSelected(pipelineId, elementId, initialValues);
    }
  })
);

const PipelineElement = ({
  elementId,
  connectDragSource,
  connectDropTarget,
  elementDefinition,
  className,
  onElementClick,
  isOver,
  canDrop,
  isDragging,
  draggingItemType
}: EnhancedProps) =>
  connectDragSource(
    connectDropTarget(
      <div className={className} onClick={onElementClick}>
        {elementDefinition && (
          <ElementImage
            className="Pipeline-element__icon"
            icon={elementDefinition.icon}
          />
        )}
        <Button className="Pipeline-element__type" text={elementId} />
      </div>
    )
  );

export default enhance(PipelineElement);
