import * as React from "react";
import { StreamTaskType } from "../../../types";

interface Props {
  onSelection: (filterId: number, trackers: Array<StreamTaskType>) => void;
}

export default ({  }: Props) => (
  <div>I.O.U One Processing List (see the TODO file)</div>
);
