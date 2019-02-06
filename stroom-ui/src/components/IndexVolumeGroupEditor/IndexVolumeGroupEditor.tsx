import * as React from "react";

import { compose } from "recompose";
import IconHeader from "../IconHeader";
import Button from "../Button";
import { withRouter, RouteComponentProps } from "react-router";

export interface Props {
  name: string;
}

export interface EnhancedProps extends Props, RouteComponentProps<any> {}

const enhance = compose<EnhancedProps, Props>(withRouter);

const IndexVolumeGroupEditor = ({ name, history }: EnhancedProps) => (
  <div>
    <IconHeader icon="database" text={name} />
    <Button text="Back" onClick={() => history.push(`/s/indexing/groups/`)} />
  </div>
);

export default enhance(IndexVolumeGroupEditor);
