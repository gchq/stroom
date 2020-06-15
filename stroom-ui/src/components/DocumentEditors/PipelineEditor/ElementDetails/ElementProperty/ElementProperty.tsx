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

import {
  getParentProperty,
  getChildValue,
  getCurrentValue,
  getElementValue,
} from "../../pipelineUtils";
import ElementPropertyInheritanceInfo from "./ElementPropertyInheritanceInfo";
import ElementPropertyField from "./ElementPropertyField";
import { PipelineEditApi } from "../../types";
import { ElementPropertyType } from "components/DocumentEditors/PipelineEditor/useElements/types";

interface Props {
  pipelineEditApi: PipelineEditApi;
  elementPropertyType: ElementPropertyType;
}

const ElementProperty: React.FunctionComponent<Props> = ({
  pipelineEditApi,
  elementPropertyType,
}) => {
  const { pipeline, selectedElementId } = pipelineEditApi;
  const value = getElementValue(
    pipeline,
    selectedElementId,
    elementPropertyType.name,
  );
  const childValue = getChildValue(
    pipeline,
    selectedElementId,
    elementPropertyType.name,
  );
  const parentValue = getParentProperty(
    pipeline.configStack,
    selectedElementId,
    elementPropertyType.name,
  );
  const currentValue = getCurrentValue(
    value,
    parentValue,
    elementPropertyType.defaultValue,
    elementPropertyType.type,
  );

  const type: string = elementPropertyType.type.toLowerCase();

  const defaultValue: any = elementPropertyType.defaultValue;
  const docRefType: string | undefined =
    elementPropertyType.docRefTypes && elementPropertyType.docRefTypes[0];
  const name: string = elementPropertyType.name;
  const description: string = elementPropertyType.description;

  return (
    <React.Fragment>
      <label>{description}</label>
      <ElementPropertyField
        {...{
          value: currentValue,
          name,
          pipelineEditApi,
          type,
          docRefType,
        }}
      />
      <div className="element-property__advice">
        <p>
          The <em>field name</em> of this property is <strong>{name}</strong>
        </p>
        <ElementPropertyInheritanceInfo
          pipelineEditApi={pipelineEditApi}
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
};

export default ElementProperty;
