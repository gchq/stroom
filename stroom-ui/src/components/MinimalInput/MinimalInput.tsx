import * as React from "react";
import styled from "styled-components";

/**
 * This is a simple minimal input with no functionality, just styling.
 *
 * The purpose is to provide what looks like edit-in-place, except it's
 * always an input and never anything else.
 *
 * It uses borders to achieve this:
 *  - No border and it just looks like test on the page
 *  - Hover shows a thin border and acts as an invitation to click
 *  - Focus shows a significant border and it looks like a normal input
 */
const MinimalInput = ({ ...rest }) => {
  // Using px instead of em because it makes the calculations for changing
  // border width and padding easier. We're changing these for two reasons:
  //  1. so the total size of the input doesn't change and move other stuff
  //  2. so the text inside the input doesn't shift when focusing
  const Input = styled.input`
    border: 1px solid white;
    padding: 3px 7px 3px 7px;
    margin: 0;
    :hover {
      border: 1px solid lightgrey;
    }

    :focus {
      padding: 3px 6px 3px 6px;
      border: 2px solid #2185d0;
    }

    :disabled {
      background: #dddddd;
    }
  `;

  return <Input {...rest} />;
};

export default MinimalInput;
