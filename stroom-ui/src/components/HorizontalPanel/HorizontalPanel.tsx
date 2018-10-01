import * as React from "react";
import { compose, lifecycle } from "recompose";
import * as Mousetrap from "mousetrap";

import Button from "../Button";

export interface Props {
  title: React.ReactNode;
  headerMenuItems?: React.ReactNode;
  content: React.ReactNode;
  onClose: (a?: any) => any;
  className?: string;
}

export interface EnhancedProps extends Props {}

const enhance = compose<EnhancedProps, Props>(
  lifecycle<Props, {}, {}>({
    componentDidMount() {
      const { onClose } = this.props;
      Mousetrap.bind("esc", () => onClose());
    },
    componentWillUnmount() {
      Mousetrap.unbind("esc");
    }
  })
);

const HorizontalPanel = ({
  title,
  headerMenuItems,
  content,
  onClose,
  className
}: EnhancedProps) => (
  <div className={`horizontal-panel ${className}`}>
    <div className="horizontal-panel__header flat">
      <div className="horizontal-panel__header__title">{title}</div>
      {headerMenuItems}
      <Button icon="times" onClick={() => onClose()} />
    </div>
    <div className="horizontal-panel__content">{content}</div>
  </div>
);

export default enhance(HorizontalPanel);
