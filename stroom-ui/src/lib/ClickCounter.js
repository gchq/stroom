class ClickCounter {
  constructor() {
    this.timer = 0;
    this.delay = 200;
    this.prevent = false;
  }

  withOnSingleClick(handler) {
    this.onSingleClickHandler = handler;
    return this;
  }

  withOnDoubleClick(handler) {
    this.onDoubleClickHandler = handler;
    return this;
  }

  onSingleClick(props) {
    this.timer = setTimeout(() => {
      if (!this.prevent) {
        this.onSingleClickHandler(props);
      }
      this.prevent = false;
    }, this.delay);
  }

  onDoubleClick(props) {
    clearTimeout(this.timer);
    this.prevent = true;
    this.onDoubleClickHandler(props);
  }
}

export default ClickCounter;
