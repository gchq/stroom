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

export interface Props {
  north?: boolean;
  west?: boolean;
  south?: boolean;
  east?: boolean;
}

const defaultProps: Props = {
  north: false,
  west: false,
  south: false,
  east: false
};

interface QuarterProps {
  vertical?: boolean;
  horizontal?: boolean;
}

const ElbowQuarter: React.StatelessComponent<QuarterProps> = ({
  vertical,
  horizontal
}) => (
  <div
    className={`ElbowLine__quarter ${vertical ? "vertical" : ""} ${
      horizontal ? "horizontal" : ""
    }`}
  />
);

ElbowQuarter.defaultProps = {
  vertical: false,
  horizontal: false
};

/**
 * This element can be used to place an elbowed line things like pipelines and expressions
 */
const ElbowLine: React.StatelessComponent<Props> = ({
  north,
  south,
  east,
  west
}) => (
  <div className="ElbowLine">
    <ElbowQuarter vertical={north!} horizontal={west!} />
    <ElbowQuarter horizontal={east!} />
    <ElbowQuarter vertical={south!} />
    <ElbowQuarter />
  </div>
);

ElbowLine.defaultProps = defaultProps;

export default ElbowLine;
