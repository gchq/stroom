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
// import { pipe } from "ramda";
// import {
//   DragSource,
//   DropTarget,
//   DragSourceSpec,
//   DragSourceCollector,
//   DropTargetSpec,
//   DropTargetCollector,
// } from "react-dnd";

import ElementImage from "../ElementImage";
// import { canMovePipelineElement } from "../pipelineUtils";
import {
  // DragDropTypes,
  DragCollectedProps,
  DropCollectedProps,
} from "../types";
// import { isValidChildType } from "../elementUtils";
import Button from "components/Button";

import { ShowDialog } from "../AddElementModal/types";
import { PipelineEditApi } from "../types";
import { ElementDefinition } from "components/DocumentEditors/PipelineEditor/useElements/types";

interface Props {
  pipelineEditApi: PipelineEditApi;
  elementId: string;
  className?: string;
  showAddElementDialog: ShowDialog;
  elementDefinition: ElementDefinition;
}

// interface DragObject {
//   pipelineId: string;
//   elementId: string;
//   elementDefinition: ElementDefinition;
// }

interface EnhancedProps extends Props, DropCollectedProps, DragCollectedProps {}

// const dragSource: DragSourceSpec<Props, DragObject> = {
//   canDrag() {
//     return true;
//   },
//   beginDrag(props) {
//     return {
//       pipelineId: props.pipelineEditApi.pipelineId,
//       elementId: props.elementId,
//       elementDefinition: props.elementDefinition,
//     };
//   },
// };
//
// const dragCollect: DragSourceCollector<DragCollectedProps, Props> = (
//   connect,
//   monitor,
// ) => ({
//   connectDragSource: connect.dragSource(),
//   isDragging: monitor.isDragging(),
// });
//
// const dropTarget: DropTargetSpec<Props> = {
//   canDrop(props, monitor) {
//     const {
//       elementId,
//       elementDefinition,
//       pipelineEditApi: { pipeline, asTree },
//     } = props;
//
//     switch (monitor.getItemType()) {
//       case DragDropTypes.ELEMENT:
//         const dropeeId = monitor.getItem().elementId;
//         const dropeeDefinition = monitor.getItem().elementDefinition;
//         const isValidChild = isValidChildType(
//           elementDefinition,
//           dropeeDefinition,
//           0,
//         );
//
//         const isValid = canMovePipelineElement(
//           pipeline,
//           asTree,
//           dropeeId,
//           elementId,
//         );
//
//         return isValidChild && isValid;
//       case DragDropTypes.PALLETE_ELEMENT:
//         const dropeeType = monitor.getItem().element;
//         if (dropeeType) {
//           const isValidChild = isValidChildType(
//             elementDefinition,
//             dropeeType,
//             0,
//           );
//           return isValidChild;
//         }
//         return true;
//
//       default:
//         return false;
//     }
//   },
//   drop(props, monitor) {
//     const {
//       elementId,
//       pipelineEditApi: {
//         elementMoved,
//         elementReinstated,
//         existingElementNames,
//       },
//       showAddElementDialog,
//     } = props;
//
//     switch (monitor.getItemType()) {
//       case DragDropTypes.ELEMENT: {
//         const newElementId = monitor.getItem().elementId;
//         elementMoved(newElementId, elementId);
//         break;
//       }
//       case DragDropTypes.PALLETE_ELEMENT: {
//         const { element, recycleData } = monitor.getItem();
//
//         if (recycleData) {
//           elementReinstated(elementId, recycleData);
//         } else {
//           showAddElementDialog(elementId, element, existingElementNames);
//         }
//         break;
//       }
//       default:
//         break;
//     }
//   },
// };
//
// const dropCollect: DropTargetCollector<DropCollectedProps, Props> = (
//   connect,
//   monitor,
// ) => ({
//   connectDropTarget: connect.dropTarget(),
//   isOver: monitor.isOver(),
//   canDrop: monitor.canDrop(),
//   draggingItemType: monitor.getItemType(),
// });

// dnd_error: temporarily disable dnd-related code to get the build working
/* const enhance = pipe(
 *   DragSource(DragDropTypes.ELEMENT, dragSource, dragCollect),
 *   DropTarget(
 *     [DragDropTypes.ELEMENT, DragDropTypes.PALLETE_ELEMENT],
 *     dropTarget,
 *     dropCollect,
 *   ),
 * ); */

export const PipelineElement: React.FunctionComponent<EnhancedProps> = ({
  elementId,
  connectDragSource,
  connectDropTarget,
  isOver,
  canDrop,
  isDragging,
  draggingItemType,
  pipelineEditApi: { elementSelected, selectedElementId },
  elementDefinition,
}) => {
  const onElementClick = React.useCallback(() => elementSelected(elementId), [
    elementId,
    elementSelected,
  ]);

  const className = React.useMemo(() => {
    const classNames = ["Pipeline-element"];
    classNames.push("raised-low");

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
        classNames.push("cannot_drop");
      }

      if (selectedElementId === elementId) {
        classNames.push("selected");
      }
    } else {
      classNames.push("borderless");
    }

    return classNames.join(" ");
  }, [
    draggingItemType,
    isDragging,
    canDrop,
    selectedElementId,
    elementId,
    isOver,
  ]);

  return connectDragSource(
    connectDropTarget(
      <div className={className} onClick={onElementClick}>
        {elementDefinition && (
          <ElementImage
            className="Pipeline-element__icon"
            icon={elementDefinition.icon}
          />
        )}
        <Button className="Pipeline-element__type" text={elementId} />
      </div>,
    ),
  );
};
