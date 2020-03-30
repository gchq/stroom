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
import { DragSourceSpec, DragSourceCollector, DragSource } from "react-dnd";

import Button from "components/Button";
import { DragDropTypes, DragObject, DragCollectedProps } from "../types";
import { RecycleBinItem } from "../types";
import ElementImage from "../ElementImage";

interface Props {
  elementWithData: RecycleBinItem;
}

const dragSource: DragSourceSpec<Props, DragObject> = {
  canDrag(props) {
    return true;
  },
  beginDrag(props) {
    return {
      ...props.elementWithData,
    };
  },
};

const dragCollect: DragSourceCollector<DragCollectedProps, Props> = (
  connect,
  monitor,
) => ({
  connectDragSource: connect.dragSource(),
  isDragging: monitor.isDragging(),
});

interface EnhancedProps extends Props, DragCollectedProps {}

const enhance = DragSource<Props, DragCollectedProps>(
  DragDropTypes.PALLETE_ELEMENT,
  dragSource,
  dragCollect,
);

const NewElement: React.FunctionComponent<EnhancedProps> = ({
  connectDragSource,
  elementWithData: { element, recycleData },
}) => {
  const [hasFocus, setHasFocus] = React.useState<boolean>(false);

  return connectDragSource(
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
    </div>,
  );
};

export default enhance(NewElement);
