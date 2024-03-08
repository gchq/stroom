
function onBackgroundVariableChange() {
  var variableName = document.getElementById("variables").value;
  var panels = document.querySelectorAll('.themed-icons-panel');

  panels.forEach((panel) => {
    panel.style.setProperty('background', variableName);
  });
}
