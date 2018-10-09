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
import { compose } from "recompose";
import { connect } from "react-redux";

import Button from "../../Button";

import { actionCreators } from "../redux";
import { GlobalStoreState } from "../../../startup/reducers";

const {
  pipelineElementPropertyRevertToParent,
  pipelineElementPropertyRevertToDefault
} = actionCreators;

export interface Props {
  pipelineId: string;
  elementId: string;
  name: string;
  value: any;
  parentValue: any;
  childValue: any;
  defaultValue: any;
  type: string;
}

interface ConnectState {}
interface ConnectDispatch {
  pipelineElementPropertyRevertToParent: typeof pipelineElementPropertyRevertToParent;
  pipelineElementPropertyRevertToDefault: typeof pipelineElementPropertyRevertToDefault;
}

interface EnhancedProps extends Props, ConnectState, ConnectDispatch {}

const enhance = compose<EnhancedProps, Props>(
  connect<ConnectState, ConnectDispatch, Props, GlobalStoreState>(
    undefined,
    {
      pipelineElementPropertyRevertToParent,
      pipelineElementPropertyRevertToDefault
    }
  )
);

/**
 * Gets a value for display from the property.
 *
 * @param {property} value The property
 * @param {string} type The type of the property
 */
const getDisplayValue = (value: any, type: string): string => {
  // If values are entities then they'll be objects, which we can't drop into JSX.
  let displayValue: string =
    value !== null && typeof value === "object" ? value.value : value;
  // And if we're dealing with a boolean then we'll want to get the string equivelent.
  if (type === "boolean") {
    displayValue = value.toString();
  }
  return displayValue;
};

/**
 * Gets the details for display and processing.
 *
 * There's a matrix of outcomes depending on what value we have, and this function
 * produces output specific to the combination of these values.
 *
 * @param {property} value The property from the top of the stack
 * @param {property} parentValue The property from the parent
 * @param {string} defaultValue The default property
 * @param {string} type The type of the property
 */
const ElementPropertyFieldDetails = ({
  pipelineId,
  elementId,
  name,
  value,
  parentValue,
  childValue,
  defaultValue,
  type,
  pipelineElementPropertyRevertToParent,
  pipelineElementPropertyRevertToDefault
}: EnhancedProps) => {
  const RevertToDefaultButton = (
    <Button
      text="Revert to default"
      onClick={() =>
        pipelineElementPropertyRevertToDefault(pipelineId, elementId, name)
      }
    />
  );
  const RevertToParentButton = (
    <Button
      text="Revert to parent"
      onClick={() =>
        pipelineElementPropertyRevertToParent(pipelineId, elementId, name)
      }
    />
  );

  // Parse the value if it's a boolean.
  if (type === "boolean") {
    defaultValue = defaultValue === "true";
  }

  // The property.value object uses integer so we might need to convert
  type = type === "int" ? "integer" : type;

  const isSet = (value: any) => value !== undefined && value !== "";

  // We never use the parentValue to set the actualValue -- if there's a parentValue then it'll appear in
  // the merged picture. We just need the parentValue so we know where we are regards inheritance.
  // This doesn't apply with the defaultValue -- it is never in the merged picture so if
  // we deduce that's the value we want we need to set it as such.

  if (value === undefined && parentValue === undefined && isSet(defaultValue)) {
    return (
      <div>
        <p>
          This property is using the default value of{" "}
          <strong>{getDisplayValue(defaultValue, type)}</strong>.
        </p>
        <p>
          It is not inheriting anything and hasn't been set to anything by a
          user.
        </p>
      </div>
    );
  } else if (
    value !== undefined &&
    parentValue === undefined &&
    isSet(defaultValue)
  ) {
    return (
      <div>
        <p>
          This property has a default value of{" "}
          <strong>{getDisplayValue(defaultValue, type)}</strong> but it has been
          overridden by the user. You can revert to the default if you like.
        </p>
        {RevertToDefaultButton}
        <p>This property is not inheriting anything.</p>
      </div>
    );
  } else if (
    value === undefined &&
    parentValue !== undefined &&
    isSet(defaultValue)
  ) {
    return (
      <div>
        <p>
          This property is currently set to the default value. It's parent has a
          value of{" "}
          <strong>{getDisplayValue(parentValue.value[type], type)}</strong>. You
          may revert to this if you wish.
        </p>
        {RevertToParentButton}
      </div>
    );
  } else if (
    value !== undefined &&
    parentValue !== undefined &&
    isSet(defaultValue)
  ) {
    const setByChild =
      childValue !== undefined && childValue.value[type] === value.value[type];
    if (setByChild) {
      return (
        <div>
          <p>
            This property has a default value of{" "}
            <strong>{getDisplayValue(defaultValue, type)}</strong>.
          </p>
          <p>
            This property would inherit a value of{" "}
            <strong>{getDisplayValue(parentValue.value[type], type)}</strong>{" "}
            except this has been set by a user.
          </p>

          <p>
            You may revert it to the default or you may revert to the parent's
            value
          </p>
          <div>
            {RevertToDefaultButton}
            {RevertToParentButton}
          </div>
        </div>
      );
    } else {
      return (
        <div>
          <p>
            This property has a default value of{" "}
            <strong>{getDisplayValue(defaultValue, type)}</strong>.
          </p>
          <p>This property is using an inherited value.</p>

          <p>You may revert it to the default if you wish.</p>
          <div>{RevertToDefaultButton}</div>
        </div>
      );
    }
  } else if (
    value === undefined &&
    parentValue === undefined &&
    !isSet(defaultValue)
  ) {
    return (
      <p>
        This property has no default value, it is not inheriting anything, and
        hasn't been set to anything by a user.
      </p>
    );
  } else if (
    value !== undefined &&
    parentValue === undefined &&
    !isSet(defaultValue)
  ) {
    return (
      <p>
        This property has no default value and it is not inheriting anything. It
        has been set by the user.
      </p>
    );
  } else if (
    value === undefined &&
    parentValue !== undefined &&
    !isSet(defaultValue)
  ) {
    return (
      <p>
        This property has no default value and has not been set to anything by
        the user, but it is inheriting a value of{" "}
        <strong>{getDisplayValue(parentValue.value[type], type)}</strong>.
      </p>
    );
  } else if (
    value !== undefined &&
    parentValue !== undefined &&
    !isSet(defaultValue)
  ) {
    const setByChild =
      childValue !== undefined && childValue.value[type] === value.value[type];
    if (setByChild) {
      return (
        <div>
          <p>This property has no default value.</p>

          <p>
            It is inheriting a value of{" "}
            <strong> {getDisplayValue(parentValue.value[type], type)}</strong>
            but this has been overriden by the user. You can revert to this
            inherited value if you like.
          </p>
          {RevertToParentButton}
        </div>
      );
    } else {
      return (
        <div>
          <p>This property has no default value.</p>

          <p>This property is inheriting it's value.</p>
        </div>
      );
    }
  }

  return (
    <div>
      <p>This property is in a weird state</p>
    </div>
  );
};

export default enhance(ElementPropertyFieldDetails);
