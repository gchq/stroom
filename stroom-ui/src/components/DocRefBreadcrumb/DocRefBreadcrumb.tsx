import * as React from "react";

import { useDocumentTree } from "components/DocumentEditors/api/explorer";
import { DocRefConsumer } from "components/DocumentEditors/useDocumentApi/types/base";

interface Props {
  docRefUuid: string;
  openDocRef: DocRefConsumer;
  className?: string;
}

const Divider: React.FunctionComponent = () => (
  <div className="DocRefBreadcrumb__divider">/</div>
);

const DocRefBreadcrumb: React.FunctionComponent<Props> = ({
  docRefUuid,
  openDocRef,
  className = "",
}) => {
  const { findDocRefWithLineage } = useDocumentTree();
  const { lineage, node } = React.useMemo(
    () => findDocRefWithLineage(docRefUuid),
    [findDocRefWithLineage, docRefUuid],
  );

  return (
    <div className={`DocRefBreadcrumb ${className || ""}`}>
      {lineage.map(l => (
        <React.Fragment key={l.uuid}>
          <Divider />
          <div
            className="DocRefBreadcrumb__link"
            title={l.name}
            onClick={() => openDocRef(l)}
          >
            {l.name}
          </div>
        </React.Fragment>
      ))}
      <Divider />
      <div className="DocRefBreadcrumb__name" title={node.name}>
        {node.name}
      </div>
    </div>
  );
};

export default DocRefBreadcrumb;
