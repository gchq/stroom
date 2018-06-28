
(function() {
var cssStr = "" +
"/*" +
" * Copyright 2016 Crown Copyright" +
" *" +
" * Licensed under the Apache License, Version 2.0 (the \"License\");" +
" * you may not use this file except in compliance with the License." +
" * You may obtain a copy of the License at" +
" *" +
" *     http://www.apache.org/licenses/LICENSE-2.0" +
" *" +
" * Unless required by applicable law or agreed to in writing, software" +
" * distributed under the License is distributed on an \"AS IS\" BASIS," +
" * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied." +
" * See the License for the specific language governing permissions and" +
" * limitations under the License." +
" */" +
"@CHARSET \"UTF-8\";" +
"" +
"html, body {" +
"	width: 100%;" +
"	height: 100%;" +
"	padding: 0px;" +
"	margin: 0px;" +
"	overflow: hidden;" +
"	font-family: Roboto, arial, tahoma, verdana;" +
"	font-size: 13px;" +
"	font-weight: 400;" +
"}" +
"" +
"text {" +
"    fill: rgba(0,0,0,0.87);" +
"}" +
"" +
".vis {" +
"	margin: 0px;" +
"	padding: 0px;" +
"}" +
"" +
".vis-text {" +
"	shape-rendering: crispEdges;" +
"}" +
"" +
".vis-line {" +
"	stroke-width: 0.5px;" +
"}" +
"" +
".vis-area {" +
"}" +
"" +
".vis-axis {" +
"}" +
"" +
".vis-axis path,line {" +
"	fill: none;" +
"	stroke: rgba(0,0,0,0.54);" +
"	shape-rendering: crispEdges;" +
"}" +
"" +
".vis-axis text {" +
"	shape-rendering: crispEdges;" +
"	font-size: 11px;" +
"        fill: rgba(0,0,0,0.54);" +
"}" +
"" +
"text.weekend {" +
"	fill: rgba(0,0,0,0.12);" +
"}" +
"" +
".vis-series {" +
"}" +
"" +
"rect.vis-coloured-element {" +
"    shape-rendering: crispEdges;" +
"}" +
"" +
".vis-barchart-element {" +
"    stroke: #fff;" +
"}" +
"" +
".vis-series path {" +
"	fill: none;" +
"}" +
"" +
".vis-marker {" +
"    position: absolute;" +
"    stroke: #000;" +
"    stroke-width: 1.5px;" +
"}" +
"" +
".vis-label {" +
"    position: absolute;" +
"    left: 0px;" +
"    top: 0px;" +
"}" +
"" +
".vis-seriesLabel {" +
"    position: absolute;" +
"    left: 0px;" +
"    top: 0px;" +
"    background-color: rgba(255,255,255,0.87);" +
"    white-space: nowrap;" +
"}" +
"" +
"svg.vis-cellVisualisation {" +
"    fill:white;" +
"}" +
"" +
"rect.vis-cellVisualisation-border {" +
"    fill: white;" +
"    stroke: rgba(0,0,0,0.12);" +
"    stroke-width: 1px;" +
"    shape-rendering: crispEdges;" +
"}" +
"" +
"text.vis-cellVisualisation-seriesName {" +
"    font-weight: 400;" +
"}" +
"" +
"text.vis-cellVisualisation-seriesName.normal {" +
"    font-size: 16px;" +
"}" +
"" +
"text.vis-cellVisualisation-seriesName.small {" +
"    font-size: 14px;" +
"}" +
"" +
"text.vis-cellVisualisation-seriesName.smaller {" +
"    font-size: 12px;" +
"}" +
"" +
"text.vis-cellVisualisation-icon {" +
"    font-family: FontAwesome;" +
"    font-size: 14px;" +
"    cursor: pointer;" +
"    /*fill: #ddd;*/" +
"    fill: rgba(0,0,0,0.38);" +
"    transition: fill 0.4s;" +
"}" +
"" +
"text.vis-cellVisualisation-icon:hover {" +
"      fill: rgba(0,0,0,0.87);" +
"}" +
"" +
".vis-glass {" +
"	position: absolute;" +
"	left: 0px;" +
"	top: 0px;" +
"	width: 100%;" +
"	height: 100%;" +
"	pointer-events: all;" +
"}" +
"" +
".grid { " +
"	display: table; " +
"} " +
"" +
".grid:after { " +
"	content: ''; " +
"	display: table; " +
"	clear: both; " +
"} " +
"" +
".row {" +
"	float: bottom; " +
"} " +
"" +
".col {" +
"	width: auto; " +
"} " +
"" +
".col-2-3 { " +
"	width: auto; " +
"} " +
"" +
"[class*='col'] { " +
"	float: left; " +
"	padding-right: 10px; " +
"} " +
"" +
"[class*='col']:last-of-type { " +
"	padding-right: 0px; " +
"} " +
"" +
".d3-tip.legend {" +
"    pointer-events: all;" +
"}" +
"" +
".legend {" +
"}" +
"" +
".legend-scrollable-container {" +
"    overflow-y: auto;" +
"    overflow-x: hidden;" +
"}" +
"" +
".legend-inner-container {" +
"}" +
"" +
".legend-row {" +
"    width: 100%;" +
"    position: relative;" +
"    min-height: 13px;" +
"}" +
"" +
".legend-key {" +
"    width: 100%;" +
"    height: 100%;" +
"    border-radius: 4px;" +
"    border-style: solid;" +
"    border-width: 2px;" +
"    /*margin: 2px;*/" +
"}" +
"" +
".legend-key-container {" +
"    width: 27px;" +
"    height: 100%;" +
"    padding: 2px;" +
"    pointer-events: all;" +
"    position: absolute;" +
"    top: 0;" +
"    left: 0;" +
"}" +
"" +
".legend-text {" +
"    overflow-wrap: break-word;" +
"    padding-left: 30px;" +
"    /*padding-top: 3px;*/" +
"    /*padding-bottom 2px;*/" +
"}" +
"" +
"html { " +
"	box-sizing: border-box; " +
"} " +
"" +
"*, *:after, *:before { " +
"	box-sizing: inherit; " +
"} " +
"" +
".d3-tip span { " +
"        color: #eee; " +
"} " +
"" +
".d3-tip { " +
"	line-height: 1.4; " +
"	padding: 7px; " +
"	background: rgba(30, 30, 30, 0.8); " +
"        color: #bbb; " +
"	border-radius: 4px; " +
"	pointer-events: none; " +
"        text-rendering: geometricPrecision;" +
"} " +
"" +
"@-webkit-keyframes fadeIn { from { opacity:0; } to { opacity:1; } }" +
"@-moz-keyframes fadeIn { from { opacity:0; } to { opacity:1; } }" +
"@keyframes fadeIn { from { opacity:0; } to { opacity:1; } }" +
"/*.d3-tip.animate {*/" +
".d3-tip.animate {" +
"	animation: fadeIn 0.25s ease-in;" +
"	-webkit-animation: fadeIn 0.25s ease-in;" +
"}" +
"" +
"/* Creates a small triangle extender for the tooltip */ " +
".d3-tip:after { " +
"	box-sizing: border-box; " +
"	display: inline; " +
"	font-size: 10px; " +
"	width: 100%; " +
"	line-height: 1; " +
"	color: rgba(30, 30, 30, 0.8); " +
"	position: absolute; " +
"	pointer-events: none; " +
"} " +
"" +
"/* Eastward tooltips */ " +
".d3-tip.e:after { " +
"	content: \"\\25C0\";" +
"	margin: -4px 0 0 0; " +
"	top: 50%; " +
"	left: -8px; " +
"} " +
"" +
"/* Westward tooltips */ " +
".d3-tip.w:after { " +
"	content: \"\\25B6\";" +
"	margin: -4px 0 0 0px; " +
"	top: 50%; " +
"	left: 100%; " +
"} " +
"" +
"/* Northward tooltips */" +
".d3-tip.n:after {" +
"  content: \"\\25BC\";" +
"  margin: -3px 0 0 0;" +
"  top: 100%;" +
"  left: 0;" +
"  text-align: center;" +
"}" +
"" +
"::-webkit-scrollbar {" +
"    width: 8px;" +
"    height: 8px;" +
"}" +
"" +
"::-webkit-scrollbar-button {" +
"    width: 0px;" +
"    height: 0px;" +
"}" +
"" +
"::-webkit-scrollbar-track {" +
"   /*-webkit-box-shadow: inset 0 0 6px rgba(0,255,0,1);*/" +
"   -webkit-border-radius: 4px;" +
"   border-radius: 4px;" +
"   border: 0px none #ffffff;" +
"   background: rgba(30,30,30,0.8);" +
"}" +
"" +
"::-webkit-scrollbar-track:hover {" +
"   background: rgba(30,30,30,0.8);" +
"}" +
"" +
"::-webkit-scrollbar-track:active {" +
"   background: rgba(30,30,30,0.8);" +
"}" +
"" +
"::-webkit-scrollbar-thumb {" +
"   -webkit-border-radius: 10px;" +
"   border-radius: 4px;" +
"   background: rgba(255,255,255,0.8);" +
"   border: 0px none #ffffff;" +
"   /*-webkit-box-shadow: inset 0 0 6px rgba(255,0,0,1);*/" +
"}" +
"" +
"::-webkit-scrollbar-thumb:active {" +
"   background: rgba(255,255,255,0.8);" +
"}" +
"" +
"::-webkit-scrollbar-thumb:hover {" +
"   background: rgba(255,255,255,0.8);" +
"}" +
"" +
"::-webkit-scrollbar-thumb:window-inactive {" +
"   background: rgba(200,200,200,0.8);" +
"}" +
"" +
"::-webkit-scrollbar-corner {" +
"   background: trasnparent;" +
"}" +
"";
d3.select(document).select("head").insert("style").text(cssStr);
})();
