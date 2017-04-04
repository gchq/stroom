<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<%@page import="stroom.util.config.StroomProperties"%>

<!--
  ~ Copyright 2016 Crown Copyright
  ~
  ~ Licensed under the Apache License, Version 2.0 (the "License");
  ~ you may not use this file except in compliance with the License.
  ~ You may obtain a copy of the License at
  ~
  ~     http://www.apache.org/licenses/LICENSE-2.0
  ~
  ~ Unless required by applicable law or agreed to in writing, software
  ~ distributed under the License is distributed on an "AS IS" BASIS,
  ~ WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  ~ See the License for the specific language governing permissions and
  ~ limitations under the License.
  -->

<!-- <html class="stroom stroom-theme-dark"> -->
<html class="stroom">
  <head>
    <meta http-equiv="content-type" content="text/html; charset=UTF-8">
    <title><%=StroomProperties.getProperty("stroom.pageTitle", "Stroom")%></title>

    <link rel="shortcut icon" href="favicon.ico" type="image/x-icon" />

    <!-- Material Design Lite -->
	<!-- <link rel="stylesheet" href="mdl/material.min.css" /> -->
    <!-- <script type="text/javascript" src='mdl/material.min.js'></script> -->

	<!-- Font Awesome -->
	<link rel="stylesheet" type="text/css" href="css/font-awesome.css">

    <!-- Roboto Font -->
	<link rel="stylesheet" type="text/css" href="css/Roboto.css">

    <!-- Roboto Monospace Font -->
    <link rel="stylesheet" type="text/css" href="css/Roboto_Mono.css">

    <!-- Standard GWT CSS plus Stroom modifications -->
    <link rel="stylesheet" href="css/standard.css" type="text/css" />
    <link rel="stylesheet" href="css/standard_mod.css" type="text/css" />

    <link rel="stylesheet" href="css/webkit-scrollbar.css" type="text/css" />
    <link rel="stylesheet" href="css/firefox-scrollbar.css" type="text/css" />
    <link rel="stylesheet" href="css/stroom-control.css" type="text/css" />
    <link rel="stylesheet" href="css/stroom-button.css" type="text/css" />
    <link rel="stylesheet" href="css/fa-button.css" type="text/css" />
    <link rel="stylesheet" href="css/stroom-dashboard.css" type="text/css" />
    <link rel="stylesheet" href="css/stroom.css" type="text/css" />
    <link rel="stylesheet" href="stroom/dynamic.css" type="text/css" />
    <link rel="stylesheet" href="css/editor.css" type="text/css" />
    
    <link rel="stylesheet" href="xsdbrowser/xsdbrowser.css" type="text/css" />      
   
    <!-- Themes -->
    <link rel="stylesheet" href="css/stroom-theme-standard.css" type="text/css" />
<!--     <link rel="stylesheet" href="css/stroom-theme-transitions.css" type="text/css" /> -->
    <link rel="stylesheet" href="css/stroom-theme-dark.css" type="text/css" />
   
    <!-- Material Design Spinner -->
    <link rel="stylesheet" href="css/spinner.min.css" type="text/css" />
    <link rel="stylesheet" href="css/spinner.custom.css" type="text/css" />

    <script type="text/javascript" src="stroom/ace/ace.js" charset="utf-8"></script>
    <script type="text/javascript" src="stroom/ace/beautify/beautify.js" charset="utf-8"></script>
    <script type="text/javascript" src="stroom/ace/theme-chrome.js" charset="utf-8"></script>
    <script type="text/javascript" src="stroom/ace/mode-text.js" charset="utf-8"></script>
    <script type="text/javascript" src="stroom/ace/mode-xml.js" charset="utf-8"></script>
    <script type="text/javascript" src="stroom/ace/mode-json.js" charset="utf-8"></script>
    <script type="text/javascript" src="stroom/ace/mode-javascript.js" charset="utf-8"></script>

    <script type="text/javascript" src='stroom/stroom.nocache.js'></script>
  </head>
  
  <!-- <body class="stroom-body" oncontextmenu="return false;"> -->
  <body class="stroom-body" <%=StroomProperties.getProperty("stroom.ui.oncontextmenu", "oncontextmenu=\"return false;\"")%>>
      <!-- Add history support -->
    <iframe src="javascript:''" id="__gwt_historyFrame" tabIndex='-1' style="position:absolute;width:0;height:0;border:0"></iframe>
    
    <div class="stroom-background">
    </div>
    <div class="tubeEffect">
    </div>
    <div id="loading">
	  <div id="loadingBox">
		<div id="loadingImage" class="mdl-spinner mdl-spinner--single-color mdl-js-spinner is-active is-upgraded" style="width:32px;height:32px" data-upgraded=",MaterialSpinner">
		  <div class="mdl-spinner__layer mdl-spinner__layer-1">
		    <div class="mdl-spinner__circle-clipper mdl-spinner__left"><div class="mdl-spinner__circle"></div></div><div class="mdl-spinner__gap-patch"><div class="mdl-spinner__circle"></div></div><div class="mdl-spinner__circle-clipper mdl-spinner__right"><div class= "mdl-spinner__circle"></div></div>
		  </div>
		  <div class="mdl-spinner__layer mdl-spinner__layer-2">
		    <div class="mdl-spinner__circle-clipper mdl-spinner__left"><div class="mdl-spinner__circle"> </div></div><div class="mdl-spinner__gap-patch"><div class="mdl-spinner__circle"></div></div><div class="mdl-spinner__circle-clipper mdl-spinner__right"><div class="mdl-spinner__circle"></div></div>
		  </div>
		  <div class="mdl-spinner__layer mdl-spinner__layer-3">
		    <div class="mdl-spinner__circle-clipper mdl-spinner__left"><div class="mdl-spinner__circle"></div></div><div class= "mdl-spinner__gap-patch"><div class="mdl-spinner__circle"></div></div><div class="mdl-spinner__circle-clipper mdl-spinner__right"><div class="mdl-spinner__circle"></div></div>
		  </div>
		  <div class="mdl-spinner__layer mdl-spinner__layer-4">
		    <div class="mdl-spinner__circle-clipper mdl-spinner__left"><div class="mdl-spinner__circle"></div></div><div class="mdl-spinner__gap-patch"><div class="mdl-spinner__circle"></div></div><div class="mdl-spinner__circle-clipper mdl-spinner__right"><div class="mdl-spinner__circle"></div></div>
		  </div>
		</div>
	    <div id="loadingTitle">Stroom</div>
	    <div id="loadingText">Loading Application. Please wait...</div>
	  </div>
	</div>
	
	<div id="logo" style="position:absolute;top:0px;left:0px;width:146px;height:35px;background:url('images/logo.png');background-repeat:no-repeat" />
  
    <noscript>
      <div style="width: 22em; position: absolute; left: 50%; margin-left: -11em; color: red; background-color:white; border: 1px solid red; padding: 4px; font-family: sans-serif">
        Your web browser must have JavaScript enabled
        in order for this application to display correctly.
      </div>
    </noscript>
  </body>
</html>
