import * as React from "react";

import { storiesOf } from "@storybook/react";

import StroomDecorator from "../../lib/storybook/StroomDecorator";
import ElementImage from "./ElementImage";

storiesOf("Element Image", module)
  .addDecorator(StroomDecorator)
  .add("default (large)", () => <ElementImage icon="ElasticSearch.svg" />)
  .add("small", () => <ElementImage size="sm" icon="kafka.svg" />)
  .add("large", () => <ElementImage size="lg" icon="stream.svg" />);
