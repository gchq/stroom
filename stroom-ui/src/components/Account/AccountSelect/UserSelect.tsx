/*
 * Copyright 2017 Crown Copyright
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
import { useState } from "react";
import AsyncSelect from "react-select/async";
import { Account } from "../types";

const loadOptions = (
  inputValue: string,
  callback: Function,
  idToken: string,
  url: string,
) => {
  const email = inputValue || "";
  fetch(`${url}/search?email=${email}`, {
    headers: {
      Accept: "application/json",
      "Content-Type": "application/json",
      Authorization: "Bearer " + idToken,
    },
    method: "get",
    mode: "cors",
  })
    .then((response) => response.json())
    .then((body) => {
      const options = body
        .sort((userA: Account, userB: Account) =>
          userA.email > userB.email ? 1 : -1,
        )
        .map((result: Account) => {
          return { value: result.id, label: result.email };
        });
      callback(options);
    });
};

const customStyles = {
  option: (provided: any) => ({
    ...provided,
    fontSize: 14,
  }),
  placeholder: () => ({
    fontSize: 14,
  }),
  input: (provided: any) => ({
    ...provided,
    fontSize: 14,
  }),
  singleValue: (provided: any) => ({
    ...provided,
    fontSize: 14,
  }),
};

//TODO: Obviously this isn't a hook, and therefore it can't use useConfig.
// I had a go at converting it when I was just getting started with hooks
// and ran into some problems. Still needs doing.
const AsyncUserSelect = (props: {
  onChange: Function;
  idToken: string;
  userServiceUrl: string;
}) => {
  const { onChange, idToken, userServiceUrl } = props;
  // eslint-disable-next-line
  const [_, setInputValue] = useState("");

  return (
    <AsyncSelect
      placeholder=""
      styles={customStyles}
      className="AsyncUserSelect"
      cacheOptions
      defaultOptions
      loadOptions={(inputValue, callback) =>
        loadOptions(inputValue, callback, idToken, userServiceUrl)
      }
      onInputChange={(value) => {
        setInputValue(value);
        return value;
      }}
      onChange={(value) => onChange("user", value)}
    />
  );
};

export default AsyncUserSelect;
