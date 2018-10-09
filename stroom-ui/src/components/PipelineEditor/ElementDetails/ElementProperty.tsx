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

import {
  getParentProperty,
  getChildValue,
  getCurrentValue,
  getElementValue
} from "../pipelineUtils";
import ElementPropertyFieldDetails from "./ElementPropertyInheritanceInfo";
import ElementPropertyField from "./ElementPropertyField";
import { ElementPropertyType } from "../../../types";
import { GlobalStoreState } from "../../../startup/reducers";
import { StoreStateById as PipelineStateStoreById } from "../redux/pipelineStatesReducer";

export interface Props {
  pipelineId: string;
  elementId: string;
  elementPropertyType: ElementPropertyType;
}

interface ConnectState {
  pipelineState: PipelineStateStoreById;
}
interface ConnectDispatch {}
interface WithProps {
  type: string;
  value: any;
  docRefTypes?: Array<string>;
  name: string;
  description: string;
  childValue: any;
  parentValue: any;
  currentValue: any;
  defaultValue: any;
}

export interface EnhancedProps
  extends Props,
    ConnectState,
    ConnectDispatch,
    WithProps {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    ({ pipelineEditor: { pipelineStates } }, { pipelineId }) => {
      const pipelineState = pipelineStates[pipelineId];
      return {
        pipelineState
      };
    }
  ),
  withProps(
    ({ pipelineState: { pipeline }, elementId, elementPropertyType }) => {
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

      const currentValue = getCurrentValue(
        value,
        parentValue,
        elementPropertyType.defaultValue,
        elementPropertyType.type
      );

      return {
        type: elementPropertyType.type.toLowerCase(),
        currentValue,
        defaultValue: elementPropertyType.defaultValue,
        parentValue,
        childValue,
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
  currentValue,
  parentValue,
  childValue,
  defaultValue
}: EnhancedProps) => (
  <React.Fragment>
    <label>{description}</label>
    <ElementPropertyField
      {...{
        value: currentValue,
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
      <ElementPropertyFieldDetails
        pipelineId={pipelineId}
        elementId={elementId}
        name={name}
        value={value}
        defaultValue={defaultValue}
        childValue={childValue}
        parentValue={parentValue}
        type={type}
      />
    </div>
  </React.Fragment>
);

export default enhance(ElementProperty);
