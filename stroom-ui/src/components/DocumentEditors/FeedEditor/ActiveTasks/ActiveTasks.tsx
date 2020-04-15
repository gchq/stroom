import * as React from "react";

interface Props {
  feedUuid: string;
}

const ActiveTasks: React.FunctionComponent<Props> = ({ feedUuid }) => {
  return <div>I.O.U A Meaningful Active Tasks display for {feedUuid}</div>;
};

export default ActiveTasks;
