import React from 'react';

const NewElement = ({ element }) => (
  <div className="element-pallete__element">
    <img className="element-pallete_icon" alt="X" src={require(`../images/${element.icon}`)} />
    {element.type}
  </div>
);

export default NewElement;
