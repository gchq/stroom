// We need to prevent up and down keys from moving the cursor around in the input

// I'd rather use Mousetrap for these shortcut keys. Historically Mousetrap
// hasn't handled keypresses that occured inside inputs or textareas.
// There were some changes to fix this, like binding specifically
// to a field. But that requires getting the element from the DOM and
// we'd rather not break outside React to do this. The other alternative
// is adding 'mousetrap' as a class to the input, but that doesn't seem to work.

// Up
const upArrow = 38;
const k = 75;
const upKeys = [upArrow, k];

// Down
const downArrow = 40;
const j = 74;
const downKeys = [downArrow, j];

const enter = 13;

const onSearchInputKeyDown = ({
  onUpKey, onDownKey, onOpenKey, onOtherKey,
}) => (e) => {
  if (onUpKey && (e.keyCode === upArrow || (e.ctrlKey && e.keyCode === k))) {
    onUpKey();
    e.preventDefault();
  } else if (onDownKey && (e.keyCode === downArrow || (e.ctrlKey && e.keyCode === j))) {
    onDownKey();
    e.preventDefault();
  } else if (onOpenKey && e.keyCode === enter) {
    onOpenKey();
    e.preventDefault();
  } else if (onOtherKey) {
    onOtherKey(e);
  }
};

export { upArrow, k, downArrow, j, enter, upKeys, downKeys, onSearchInputKeyDown };
