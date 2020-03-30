import * as React from "react";

import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

import NewElement from "./NewElement";
import { ElementCategories } from "../ElementCategories";
import { RecycleBinItem } from "../types";

interface Props {
  category: string;
  elementsWithData: RecycleBinItem[];
}

const ElementCategory: React.FunctionComponent<Props> = ({
  category,
  elementsWithData,
}) => {
  const [isOpen, setIsOpen] = React.useState<boolean>(true);

  if (elementsWithData.length === 0) {
    return null;
  }

  const displayTitle: string = ElementCategories[category]
    ? ElementCategories[category].displayName
    : category;

  // console.log("Fubar", {
  //   category,
  //   elementsWithData
  // });
  return (
    <div className="element-palette-category">
      <div onClick={() => setIsOpen(!isOpen)}>
        <FontAwesomeIcon
          icon={isOpen ? "caret-right" : "caret-down"}
          className="borderless"
        />{" "}
        {displayTitle}
      </div>
      <div className="page">
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
};

export default ElementCategory;
