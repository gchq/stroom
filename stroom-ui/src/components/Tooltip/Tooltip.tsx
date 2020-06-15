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
import ReactTooltip from "react-tooltip";
import v4 from "uuid/v4";

interface Props {
  trigger: React.ReactNode;
  content: React.ReactNode;
}

const Tooltip: React.FunctionComponent<Props> = ({ trigger, content }) => {
  const uuid = React.useMemo(() => v4(), []);

  return (
    <React.Fragment>
      <button data-tip data-for={uuid}>
        {trigger}
      </button>
      <ReactTooltip
        id={uuid}
        delayShow={1000}
        className="tooltip-popup raised-low"
        effect="solid"
      >
        {content}
      </ReactTooltip>
    </React.Fragment>
  );
};

export default Tooltip;
