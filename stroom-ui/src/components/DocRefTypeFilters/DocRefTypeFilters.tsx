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

import DocRefImage from "../DocRefImage";
import useDocRefTypes from "components/DocumentEditors/api/explorer/useDocRefTypes";
import { ControlledInput } from "lib/useForm/types";

enum AllSelectState {
  ALL,
  NONE,
  INDETERMINATE,
}

type Props = ControlledInput<string[]>;

let DocRefTypeFilters: React.FunctionComponent<Props> = ({
  onChange,
  value,
}) => {
  const docRefTypes: string[] = useDocRefTypes();

  let allSelectState = AllSelectState.INDETERMINATE;
  if (value.length === 0) {
    allSelectState = AllSelectState.NONE;
  } else if (value.length === docRefTypes.length) {
    allSelectState = AllSelectState.ALL;
  }

  const onAllCheckboxChanged = () => {
    switch (allSelectState) {
      case AllSelectState.ALL:
      case AllSelectState.INDETERMINATE:
        onChange([]);
        break;
      case AllSelectState.NONE:
        onChange(docRefTypes);
        break;
      default:
        break;
    }
  };

  return (
    <React.Fragment>
      <div>
        <DocRefImage size="sm" docRefType="System" />
        <label>All</label>
        <input
          type="checkbox"
          checked={allSelectState === AllSelectState.ALL}
          onChange={onAllCheckboxChanged}
        />
        ;
      </div>
      {docRefTypes
        .map(docRefType => ({
          docRefType,
          isSelected: value.includes(docRefType),
        }))
        .map(({ docRefType, isSelected }) => (
          <div key={docRefType}>
            <DocRefImage size="sm" docRefType={docRefType} />
            <label>{docRefType}</label>
            <input
              type="checkbox"
              checked={isSelected}
              onChange={() => {
                if (isSelected) {
                  onChange(value.filter(v => v !== docRefType));
                } else {
                  onChange(value.concat([docRefType]));
                }
              }}
            />
          </div>
        ))}
    </React.Fragment>
  );
};

export default DocRefTypeFilters;
