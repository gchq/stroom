import * as React from "react";

interface Props {
  feedUuid: string;
}

const FeedSettings: React.FunctionComponent<Props> = ({ feedUuid }) => {
  return <div>I.O.U One Meaninful Feed Settings Editor for {feedUuid}</div>;
};

export default FeedSettings;
