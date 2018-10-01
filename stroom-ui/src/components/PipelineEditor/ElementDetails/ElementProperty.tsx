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
import { compose, withProps } from "recompose";
import { connect } from "react-redux";

import { actionCreators } from "../redux";
import {
  getParentProperty,
  getChildValue,
  getElementValue
} from "../pipelineUtils";
import { getDetails } from "./elementDetailsUtils";
import ElementPropertyField from "./ElementPropertyField";
import { ElementPropertyType } from "../../../types";
import { GlobalStoreState } from "../../../startup/reducers";
import { StoreStateById as PipelineStateStoreById } from "../../PipelineEditor/redux/pipelineStatesReducer";

const {
  pipelineElementPropertyRevertToParent,
  pipelineElementPropertyRevertToDefault
} = actionCreators;

export interface Props {
  pipelineId: string;
  elementId: string;
  elementPropertyType: ElementPropertyType;
}

interface ConnectState {
  pipelineState: PipelineStateStoreById;
}
interface ConnectDispatch {
  pipelineElementPropertyRevertToParent: typeof pipelineElementPropertyRevertToParent;
  pipelineElementPropertyRevertToDefault: typeof pipelineElementPropertyRevertToDefault;
}
interface WithProps {
  type: string;
  value: any;
  inheritanceAdvice: string;
  docRefTypes?: Array<string>;
  name: string;
  description: string;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithProps {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ pipelineEditor: { pipelineStates, elements } }, { pipelineId }) => {
      const pipelineState = pipelineStates[pipelineId];
      return {
        pipelineState
      };
    },
    {
      pipelineElementPropertyRevertToParent,
      pipelineElementPropertyRevertToDefault
    }
  ),
  withProps(
    ({
      pipelineElementPropertyRevertToParent,
      pipelineElementPropertyRevertToDefault,
      pipelineState: { pipeline },
      elementId,
      pipelineId,
      elementPropertyType
    }) => {
      const value = getElementValue(
        pipeline,
        elementId,
        elementPropertyType.name
      );
      const childValue = getChildValue(
        pipeline,
        elementId,
        elementPropertyType.name
      );
      const parentValue = getParentProperty(
        pipeline.configStack,
        elementId,
        elementPropertyType.name
      );

      const details = getDetails({
        value,
        parentValue,
        defaultValue: elementPropertyType.defaultValue,
        type: elementPropertyType.type,
        pipelineElementPropertyRevertToParent,
        pipelineElementPropertyRevertToDefault,
        elementId,
        name: elementPropertyType.name,
        pipelineId,
        childValue
      });

      return {
        type: elementPropertyType.type.toLowerCase(),
        value: details.actualValue,
        inheritanceAdvice: details.info,
        docRefTypes: elementPropertyType.docRefTypes,
        name: elementPropertyType.name,
        description: elementPropertyType.description
      };
    }
  )
);

const ElementProperty = ({
  name,
  description,
  type,
  docRefTypes,
  pipelineId,
  elementId,
  value,
  inheritanceAdvice
}: EnhancedProps) => (
  <React.Fragment>
    <label>{description}</label>
    <ElementPropertyField
      {...{
        value,
        name,
        pipelineId,
        elementId,
        type,
        docRefTypes
      }}
    />
    <div className="element-property__advice">
      <p>
        The <em>field name</em> of this property is <strong>{name}</strong>
      </p>
      {inheritanceAdvice}
    </div>
  </React.Fragment>
);

export default enhance(ElementProperty);
