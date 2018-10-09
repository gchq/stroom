import * as React from "react";
import { compose, withHandlers } from "recompose";
import { withRouter, RouteComponentProps } from "react-router-dom";

import AppSearchBar from "../AppSearchBar";
import { DocRefIconHeader } from "../IconHeader";
import DocRefBreadcrumb from "../DocRefBreadcrumb";
import Button, { Props as ButtonProps } from "../Button";
import { DocRefConsumer, DocRefType } from "../../types";

export interface Props {
  actionBarItems: Array<ButtonProps>;
  docRef: DocRefType;
  children?: React.ReactNode;
}

interface WithHandlers {
  openDocRef: DocRefConsumer;
}

export interface EnhancedProps
  extends Props,
    RouteComponentProps<any>,
    WithHandlers {}

const enhance = compose<EnhancedProps, Props>(
  withRouter,
  withHandlers<Props & RouteComponentProps<any>, WithHandlers>({
    openDocRef: ({ history }) => d => history.push(`/s/doc/${d.type}/${d.uuid}`)
  })
);

const DocRefEditor = ({
  docRef,
  openDocRef,
  actionBarItems,
  children
}: EnhancedProps) => (
  <div className="DocRefEditor">
    <AppSearchBar
      pickerId="doc-ref-editor-app-search"
      className="DocRefEditor__searchBar"
      onChange={openDocRef}
    />

    <DocRefIconHeader
      docRefType={docRef.type}
      className="DocRefEditor__header"
      text={docRef.uuid}
    />

    <DocRefBreadcrumb
      className="DocRefEditor__breadcrumb"
      docRefUuid={docRef.uuid}
      openDocRef={openDocRef}
    />

    <div className="DocRefEditor__actionButtons">
      {actionBarItems.map((actionBarItem, i) => (
        <Button key={i} circular {...actionBarItem} />
      ))}
    </div>
    <div className="DocRefEditor__main">{children}</div>
  </div>
);

export default enhance(DocRefEditor);
