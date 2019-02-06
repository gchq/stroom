import * as React from "react";

import { compose } from "recompose";
import IconHeader from "../IconHeader";
import Button from "../Button";
import { withRouter, RouteComponentProps } from "react-router";

export interface Props {
  id: number;
}

export interface EnhancedProps extends Props, RouteComponentProps<any> {}

const enhance = compose<EnhancedProps, Props>(withRouter);

const IndexVolumeEditor = ({ id, history }: EnhancedProps) => (
  <div>
    <IconHeader icon="database" text={`Index Volume ${id}`} />
    <Button text="Back" onClick={() => history.push(`/s/indexing/volumes/`)} />
  </div>
);

export default enhance(IndexVolumeEditor);
