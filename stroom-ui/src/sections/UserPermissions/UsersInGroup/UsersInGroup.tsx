import * as React from "react";

import { compose } from "recompose";
import { User } from "../../../types";

export interface Props {
  group: User;
}

export interface EnhancedProps extends Props {}

const enhance = compose<EnhancedProps, Props>();

const UsersInGroup = ({ group }: Props) => (
  <div>Users In Group {group.name}</div>
);

export default enhance(UsersInGroup);
