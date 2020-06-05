/*
 * Copyright 2019 Crown Copyright
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
import { Account } from "components/users/types";
import styled from "styled-components";
import { Select } from "antd";

interface Props {
  onChange: (user: string) => void;
  onSearch: (search: string) => void;
  options: Account[];
}

const { Option } = Select;

/**
 * We need to override the values of input because we have some CSS somewhere
 * that adds padding and margin, and this breaks the look of the antd Select.
 */
const StyledSelect = styled(Select)`
  input {
    margin: 0;
    padding: 0;
  }
`;

const UserSelect: React.FunctionComponent<Props> = ({
  onChange,
  onSearch,
  options,
}) => (
  // We don't need to specify a value because this is read only
  <Select
    // showSearch
    // placeholder="Search for a user"
    // onChange={(email: string) => onChange(email)}
    // onSearch={(search: string) => onSearch(search)}
  >
    {options.map(option => (
      <Option key={option.email}>{option.email}</Option>
    ))}
  </Select>
);

export default UserSelect;
