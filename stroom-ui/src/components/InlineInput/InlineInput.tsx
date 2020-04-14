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
import { FunctionComponent, InputHTMLAttributes, useCallback } from "react";
import styled from "styled-components";

const InlineInput: FunctionComponent<InputHTMLAttributes<HTMLInputElement>> = ({
  onChange,
  value,
  width,
  ...rest
}) => {
  const [isEditing, setEditing] = React.useState(false);
  const handleEdit = useCallback(() => setEditing(true), [setEditing]);
  if (isEditing) {
    const StyledInput = styled.input`
      width: ${width}em;
    `;

    return (
      <StyledInput
        autoFocus={true}
        className="inline-input__editing"
        onBlur={() => {
          // Blurring sets the value
          setEditing(false);
        }}
        onChange={onChange}
        type="text"
        value={value}
        onKeyDown={event => {
          if (event.key === "Enter") {
            event.preventDefault();
            // 'Enter' sets the value
            setEditing(false);
          } else if (event.key === "Escape") {
            event.preventDefault();
            // 'Escape' does not set the value, and we need to update the
            // editing value to the original.
            setEditing(false);
          }
        }}
        {...rest}
      />
    );
  } else {
    const textToDisplay = !!value ? value : "click to edit";
    return (
      <span className="inline-input__not-editing" onClick={handleEdit}>
        {textToDisplay}
      </span>
    );
  }
};

export default InlineInput;
