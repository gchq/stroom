import * as React from "react";

import { compose } from "recompose";

export interface Props {
  name: string;
}

export interface EnhancedProps extends Props {}

const enhance = compose<EnhancedProps, Props>();

const IndexVolumeGroupEditor = ({ name }: EnhancedProps) => (
  <div>Let's edit the index volume group {name}</div>
);

export default enhance(IndexVolumeGroupEditor);
