window.globalResizeHandler = null;

const onElementResize = (element) => {
    if (window.globalResizeHandler) {
        window.globalResizeHandler(element);
    }
}

const resizeObserverHandler = (entries) => {
    entries.forEach(entry => onElementResize(entry.target));
}

window.globalResizeObserver = new ResizeObserver(resizeObserverHandler);