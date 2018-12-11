import * as React from "react";

import {
  compose,
  withState,
  withProps,
  branch,
  renderNothing
} from "recompose";

import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

import NewElement from "./NewElement";
import { ElementCategories } from "../ElementCategories";
import { RecycleBinItem } from "../pipelineUtils";

const withCategoryIsOpen = withState("isOpen", "setIsOpen", true);

export interface Props {
  category: string;
  elementsWithData: Array<RecycleBinItem>;
}

interface WithCategoryIsOpen {
  isOpen: boolean;
  setIsOpen: (value: boolean) => any;
}

interface WithProps {
  displayTitle: string;
}

export interface EnhancedProps extends Props, WithCategoryIsOpen, WithProps {}

const enhance = compose<EnhancedProps, Props>(
  withCategoryIsOpen,
  withProps(({ category }) => ({
    displayTitle: ElementCategories[category]
      ? ElementCategories[category].displayName
      : category
  })),
  branch(({ elementsWithData }) => elementsWithData.length === 0, renderNothing)
);

const ElementCategory = ({
  category,
  elementsWithData,
  isOpen,
  setIsOpen,
  displayTitle
}: EnhancedProps) => (
  <div className="element-palette-category">
    <div onClick={() => setIsOpen(!isOpen)}>
      <FontAwesomeIcon
        icon={isOpen ? "caret-right" : "caret-down"}
        className="borderless"
      />{" "}
      {displayTitle}
    </div>
    <div className="flat">
      <div
        className={`element-palette-category__elements--${
          isOpen ? "open" : "closed"
        }`}
      >
        {elementsWithData.map((e: RecycleBinItem) => (
          <NewElement key={e.element.type} elementWithData={e} />
        ))}
      </div>
    </div>
  </div>
);

export default enhance(ElementCategory);
