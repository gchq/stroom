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

import InlineMultiInput from "components/InlineMultiInput/InlineMultiInput";
import { ControlledInput } from "lib/useForm/types";
import * as React from "react";

/**
 * Capture multiple values for comparison. The value and onChange transact in CSV
 */
const InValueWidget: React.FunctionComponent<ControlledInput<any>> = ({
  value,
  onChange,
}) => {
  const splitValues: string[] = React.useMemo(
    () => (!!value && value.length > 0 ? value.split(",") : []),
    [value],
  );

  const handleChange = React.useCallback(
    (newValue: string[]) => onChange(newValue.join(",")),
    [onChange],
  );
  return <InlineMultiInput value={splitValues} onChange={handleChange} />;
};

export default InValueWidget;
