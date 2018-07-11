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

  onSingleClick() {
    this.timer = setTimeout(() => {
      if (!this.prevent) {
        this.onSingleClickHandler();
      }
      this.prevent = false;
    }, this.delay);
  }

  onDoubleClick() {
    clearTimeout(this.timer);
    this.prevent = true;
    this.onDoubleClickHandler();
  }
}

export default ClickCounter;
