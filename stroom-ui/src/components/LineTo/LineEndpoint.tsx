import * as React from "react";
import LineContext from "./LineContext";

interface Props
  extends React.DetailedHTMLProps<
    React.HTMLAttributes<HTMLDivElement>,
    HTMLDivElement
  > {
  lineEndpointId: string;
}

const LineEndpoint: React.FunctionComponent<Props> = ({
  children,
  lineEndpointId,
  ...props
}) => {
  const { getEndpointId } = React.useContext(LineContext);

  const fullId = React.useMemo(() => getEndpointId(lineEndpointId), [
    getEndpointId,
    lineEndpointId,
  ]);

  return (
    <div id={fullId} {...props}>
      {children}
    </div>
  );
};

export default LineEndpoint;
