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
import { compose, withState } from "recompose";
import { DragSource, DragSourceSpec, DragSourceCollector } from "react-dnd";

import ElementImage from "../../ElementImage";
import Button from "../../Button";
import {
  DragDropTypes,
  DragObject,
  DragCollectedProps
} from "../dragDropTypes";
import { RecycleBinItem } from "../pipelineUtils";

const withFocus = withState("hasFocus", "setHasFocus", false);

export interface Props {
  elementWithData: RecycleBinItem;
}

interface WithFocus {
  hasFocus: boolean;
  setHasFocus: (value: boolean) => any;
}

const dragSource: DragSourceSpec<Props & WithFocus, DragObject> = {
  canDrag(props) {
    return true;
  },
  beginDrag(props) {
    return {
      ...props.elementWithData
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

export interface EnhancedProps extends Props, WithFocus, DragCollectedProps {}

const enhance = compose<EnhancedProps, Props>(
  withFocus,
  DragSource(DragDropTypes.PALLETE_ELEMENT, dragSource, dragCollect)
);

const NewElement = ({
  connectDragSource,
  isDragging,
  elementWithData: { element, recycleData },
  hasFocus,
  setHasFocus
}: EnhancedProps) =>
  connectDragSource(
    <div
      className={`Pipeline-element raised-low borderless ${
        hasFocus ? "focus" : "no-focus"
      }`}
    >
      <ElementImage className="Pipeline-element__icon" icon={element.icon} />
      <Button
        className="Pipeline-element__type"
        onFocus={() => setHasFocus(true)}
        onBlur={() => setHasFocus(false)}
        text={recycleData ? recycleData.id : element.type}
      />
    </div>
  );

export default enhance(NewElement);
