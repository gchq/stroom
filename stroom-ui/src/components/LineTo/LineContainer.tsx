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

import useListReducer from "lib/useListReducer";
import * as React from "react";
import * as uuidv4 from "uuid/v4";
import LineContext from "./LineContext";
import StraightLine from "./lineCreators/StraightLine";
import LinesSvg from "./LinesSvg";
import { LineElementCreator, LineType } from "./types";

export interface Props {
  className?: string;
  LineElementCreator?: LineElementCreator;
}

const LineContainer: React.FunctionComponent<Props> = ({
  LineElementCreator = StraightLine,
  className,
  children,
}) => {
  const {
    addItem: createLine,
    items: rawLines,
    removeItem: destroyLine,
  } = useListReducer<LineType>(l => l.lineId);

  const lineContextId = React.useMemo(() => uuidv4(), []);
  const getEndpointId = React.useCallback(
    (identity: string) => `${lineContextId}-${identity}`,
    [lineContextId],
  );

  return (
    <LineContext.Provider
      value={{
        lineContextId,
        getEndpointId,
        createLine,
        destroyLine,
        rawLines,
      }}
    >
      <div className={className}>
        <LinesSvg LineElementCreator={LineElementCreator} />
        {children}
      </div>
    </LineContext.Provider>
  );
};

export default LineContainer;
