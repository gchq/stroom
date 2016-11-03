/*
 * Copyright 2016 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/*
 * Script to hold various common constants and functions used for building visualisations
 *
 * REQUIRES:
 * 		d3
 * 		font-awesome
 */

//TODO This has become pretty big and a bit of a mess, could do with breaking it 
//up into more manageable chunks.  ALso some of the fucntions are currently only
//being used by GenericGrid so should be moved into there


if (!visualisations) {
    var visualisations = {};
}

!function() {
    // Common Constants
    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    var commonConstants = {};

    // DO NOT USE commonConstants.m
    // provided for backward compatibility with some older vis
    // top, right, bottom, left
    commonConstants.m = [ 5, 5, 5, 5 ];
    commonConstants.m.top = 5;
    commonConstants.m.right = 5;
    commonConstants.m.bottom = 5;
    commonConstants.m.left = 5;

    //Held as a function so the original values are preserved when the vis mutates the margins
    commonConstants.margins = function() {
        return {
            top: 5,
            right: 5,
            bottom: 5,
            left: 5
        };
    };

    //Google's Material Design text colours
    commonConstants.googlePrimaryText = "rgba(0,0,0,0.87)";
    commonConstants.googleSecondaryText = "rgba(0,0,0,0.54)";
    commonConstants.googleDisabledOrHintText = "rgba(0,0,0,0.38)";
    commonConstants.googleDividers = "rgba(0,0,0,0.12)";

    //These are Google's Material Design colours
    commonConstants.googleRed900 = "#b71c1c";
    commonConstants.googleRed500 = "#f44336";
    commonConstants.googleRed200 = "#ef9a9a";
    commonConstants.googlePink900 = "#880e4f";
    commonConstants.googlePink500 = "#E91E63";
    commonConstants.googlePink200 = "#f48fb1";
    commonConstants.googlePurple900 = "#4a148c";
    commonConstants.googlePurple500 = "#9c27b0";
    commonConstants.googlePurple200 = "#ce93db";
    commonConstants.googleDeepPurple900 = "#311b92";
    commonConstants.googleDeepPurple500 = "#673ab7";
    commonConstants.googleDeepPurple200 = "#b39ddb";
    commonConstants.googleIndigo900 = "#1a237e";
    commonConstants.googleIndigo500 = "#3f51b5";
    commonConstants.googleIndigo200 = "#9fa8da";
    commonConstants.googleBlue900 = "#0d47a1";
    commonConstants.googleBlue500 = "#2196F3";
    commonConstants.googleBlue200 = "#90caf9";
    commonConstants.googleLightBlue900 = "#01579b";
    commonConstants.googleLightBlue500 = "#03a9f4";
    commonConstants.googleLightBlue200 = "#81d4fa";
    commonConstants.googleCyan900 = "#006064";
    commonConstants.googleCyan500 = "#00bcd4";
    commonConstants.googleCyan200 = "#80deea";
    commonConstants.googleTeal900 = "#004d40";
    commonConstants.googleTeal500 = "#009688";
    commonConstants.googleTeal200 = "#80cbc4";
    commonConstants.googleGreen900 = "#1b5e20";
    commonConstants.googleGreen500 = "#4caf50";
    commonConstants.googleGreen200 = "#a5d6a7";
    commonConstants.googleLightGreen900 = "#33691e";
    commonConstants.googleLightGreen500 = "#8bc34a";
    commonConstants.googleLightGreen200 = "#c5e1a5";
    commonConstants.googleLime900 = "#827717";
    commonConstants.googleLime500 = "#cddc39";
    commonConstants.googleLime200 = "#e6ee9c";
    commonConstants.googleYellow900 = "#f57f17";
    commonConstants.googleYellow500 = "#ffeb3b";
    commonConstants.googleYellow200 = "#fff59d";
    commonConstants.googleAmber900 = "#ff6f00";
    commonConstants.googleAmber500 = "#ffc107";
    commonConstants.googleAmber200 = "#ffe082";
    commonConstants.googleOrange900 = "#e65100";
    commonConstants.googleOrange500 = "#ff9800";
    commonConstants.googleOrange200 = "#ffcc80";
    commonConstants.googleDeepOrange900 = "#bf360c";
    commonConstants.googleDeepOrange500 = "#ff5722";
    commonConstants.googleDeepOrange200 = "#ffab91";
    commonConstants.googleBrown900 = "#3e2723";
    commonConstants.googleBrown500 = "#795548";
    commonConstants.googleBrown200 = "#bcaaa4";
    commonConstants.googleGrey900 = "#212121";
    commonConstants.googleGrey500 = "#9e9e9e";
    commonConstants.googleGrey200 = "#eeeeee";
    commonConstants.googleBlueGrey900 = "#263238";
    commonConstants.googleBlueGrey500 = "#607d8b";
    commonConstants.googleBlueGrey200 = "#b0bec5";

    //Array of google material design colours for use with d3 colour scale ranges
    //Uses most of the 500 colours first then repeats all the colours in 200 form.
    //The 200 colours (being acent colurs) are less desirable on a visualisation
    //but will only be called once all the 500 colours have been used up
    //Some colours are commented out as they are too close to other colours
    //see ../sandbox/stroomColourScale.html to view the scale
    var googleColourRange = [
        //first choice 500 colours
        commonConstants.googleBlue500,
        commonConstants.googleRed500,
        commonConstants.googleGreen500,
        commonConstants.googleOrange500,
        commonConstants.googleIndigo500,
        commonConstants.googlePink500,
        commonConstants.googleTeal500,
        commonConstants.googleDeepPurple500,
        commonConstants.googleLightGreen500,
        commonConstants.googleBlueGrey500,
        commonConstants.googleDeepOrange500,
        commonConstants.googleYellow500,
        commonConstants.googleCyan500,
        commonConstants.googleBrown500,
        commonConstants.googleAmber500,
        commonConstants.googleGrey500,
        //first choice 200 colours
        commonConstants.googleBlue200,
        commonConstants.googleRed200,
        commonConstants.googleGreen200,
        commonConstants.googleOrange200,
        commonConstants.googleIndigo200,
        commonConstants.googlePink200,
        commonConstants.googleTeal200,
        commonConstants.googleDeepPurple200,
        commonConstants.googleLightGreen200,
        commonConstants.googleBlueGrey200,
        commonConstants.googleDeepOrange200,
        commonConstants.googleYellow200,
        commonConstants.googleCyan200,
        commonConstants.googleBrown200,
        commonConstants.googleAmber200,
        //first choice 900 colours
        commonConstants.googleBlue900,
        commonConstants.googleRed900,
        commonConstants.googleGreen900,
        commonConstants.googleOrange900,
        commonConstants.googleIndigo900,
        commonConstants.googlePink900,
        commonConstants.googleTeal900,
        commonConstants.googleDeepPurple900,
        commonConstants.googleLightGreen900,
        commonConstants.googleBlueGrey900,
        commonConstants.googleDeepOrange900,
        commonConstants.googleYellow900,
        commonConstants.googleCyan900,
        commonConstants.googleBrown900,
        commonConstants.googleAmber900,
        //second choice 500 colours
        commonConstants.googleLime500,
        commonConstants.googlePurple500,
        commonConstants.googleLightBlue500,
        //second choice 200 colours
        commonConstants.googleLime200,
        commonConstants.googlePurple200,
        commonConstants.googleLightBlue200,
        //second choice 900 colours
        commonConstants.googleLime900,
        commonConstants.googlePurple900,
        commonConstants.googleLightBlue900

        //commonConstants.googleGrey200
    ];

    //D3 ordinal colour scale using Google's Material Design colours
    commonConstants.categoryGoogle = function(){
        return d3.scale.ordinal()
            .range(googleColourRange);
    };

    //D3 scale types
    commonConstants.d3ScaleLinear = "linear";
    commonConstants.d3ScaleOrdinal = "ordinal";
    commonConstants.d3ScaleUtc = "utc";

    commonConstants.transitionDuration = 500;

    commonConstants.days = [ "Mon", "Tue", "Wed", "Thur", "Fri", "Sat", "Sun" ];
    commonConstants.hours = [ 
        "00", "01", "02", "03", "04", "05", "06", "07",
        "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19",
        "20", "21", "22", "23" 
    ];

    commonConstants.millisInSecond = 1000;
    commonConstants.millisInMinute = commonConstants.millisInSecond * 60;
    commonConstants.millisInHour = commonConstants.millisInMinute * 60;
    commonConstants.millisInDay = commonConstants.millisInHour * 24;
    commonConstants.millisInWeek = commonConstants.millisInDay * 7;

    //Font Awesome
    commonConstants.fontAwesomeMove = "\uf047";
    commonConstants.fontAwesomeClose = "\uf00d";
    commonConstants.fontAwesomeCircle = "\uf111";
    commonConstants.fontAwesomeExpand = "\uf065";
    commonConstants.fontAwesomeCompress = "\uf066";
    commonConstants.fontAwesomeLegend = "\uf0ca";

    commonConstants.maxAxisLableLength = 70;

    // various colour scales for use in things like heat maps
    // use http://colorbrewer2.org/
    // (The brewer colour sclaes are available as predefined arrays in chroma.js(chroma.brewer))
    // or meyerweb.com/eric/tools/color-blend
    commonConstants.heatMapColourRanges = {
        reds: ["#555555","#ffeda0","#fed976","#feb24c","#fd8d3c","#fc4e2a","#e31a1c","#bd0026","#800026"],
        yellowToRed: [ "#ffffcc", "#ffeda0", "#fed976", "#feb24c","#fd8d3c", "#fc4e2a", "#e31a1c", "#bd0026", "#800026" ],
        greyToRed: ["#624c4c","#6f4242","#7d3939","#8a2f2f","#972626","#a41c1c","#b21313","#bf0909","#cc0000"],
        greenToRed: ["#00ff00","#00ff84","#00ffff","#0084ff","#0000ff","#8400ff","#ff00ff","#ff0088","#ff0000"],
        greenyBlues: ["#ffffd9","#edf8b1","#c7e9b4","#7fcdbb","#41b6c4","#1d91c0","#225ea8","#253494","#081d58"],
        greyscale: ["#ffffff","#eeeeee","#dddddd","#cccccc","#bbbbbb","#aaaaaa","#999999","#888888","#777777"]
    };
    commonConstants.heatMapColours = commonConstants.heatMapColourRanges.greenyBlues;

    // Common Functions
    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

    var commonFunctions = {};

    commonFunctions.addDelegateEvent = function(rootObj, event, targetSelector,
        handler) {
        return rootObj.on(event, function() {
            var eventTarget = d3.event.target;
            var target = rootObj.selectAll(targetSelector);
            target
                .each(function() {
                    // only perform event handler if the eventTarget and intendedTarget
                    // match
                    if (eventTarget === this) {
                        handler.call(eventTarget, eventTarget.__data__);
                    } else if (eventTarget.parentNode == this) {
                        handler.call(eventTarget.parentNode,
                            eventTarget.parentNode.__data__);
                    } else if (eventTarget.parentNode.parentNode == this) {
                        handler.call(eventTarget.parentNode.parentNode,
                            eventTarget.parentNode.parentNode.__data__);
                    }
                });
        });
    };

    /*
     * Function to build the html for d3-tip for a simple text string inside a div
     * You can optionally suply an extraction function to extract a string from the 
     * passed tipData object, if not supplied then it will just use tipData as is
     */
    commonFunctions.makeD3TipBasicTextHtmlFunc = function(extractionFunc) {

        var theExtractionFunc;
        if (typeof extractionFunc == 'undefined') {
            theExtractionFunc = function(d) {return d;};
        } else {
            theExtractionFunc = extractionFunc;
        }

        return function(tipValue) {
            var html = '<div><span>' + theExtractionFunc(tipValue) + '</span></div>';
            return html;
        };
    };

    commonFunctions.d3TipDirectionFunc = function(tipData) {
        //orient the tip west or east depending on which half of the screen
        //the cursor is in to avoid edge clashes
        var pageX = d3.event.pageX;
        var pageWidth = window.innerWidth;
        return (pageX > (pageWidth / 2)) ? 'w' : 'e';
    };

    /*
     * Function to initialise a reverse highlight object for providing the
     * means to add hover tips to a d3 visualisation and making all other 
     * svg elements in the vis translucent, thus highlighting the element
     * the mouse is over.
     *
     *   var inverseHighlight = commonFunctions.inverseHighlight();
     *
     * To use it you will need to get a tip instance either by building your own
     *
     *   var tip = d3.tip().............
     *   inverseHighlight.tip(tip);
     *
     * or accepting the default one
     *
     *   var tip = inverseHighlight.tip();
     *
     * You then need to call tip from a d3 selection, i.e. an svg:g node that contains the hoverable vis elements, e.g.
     *
     *   seriesContainer.call(tip);
     *
     * To set up the mouse handlers do something like this
     *  
     *   commonFunctions.addDelegateEvent(e, "mouseover", "rect", inverseHighlight.makeInverseHighlightMouseOverHandler(seriesData.key, visData.types, seriesContainer, "rect"));
     *   commonFunctions.addDelegateEvent(e, "mouseout", "rect", inverseHighlight.makeInverseHighlightMouseOutHandler(seriesContainer, "rect"));
     */
    commonFunctions.inverseHighlight = function () {
        var inverseHighlight = {};

        //variable used by the various *inverseHighlight* functions
        var highlight = null;
        var tip

        var buildDefaultTip = function() {

            return d3.tip()
                .attr('class', 'd3-tip')
                .direction(commonFunctions.d3TipDirectionFunc)
                .offset(function(tipData) {
                    if (this.direction()() == "w"){
                        return [0, -8];
                    } else if (this.direction()() == "e"){
                        return [0,8];
                    } else if (this.direction()() == "n") {
                        return [-8,0];
                    } else {
                        return [0,0];
                    }
                })
                .html(function(tipData) { 
                    var builder = inverseHighlight.htmlBuilder()
                        .addTipEntry("Series",tipData.key);

                    if (typeof(tipData.values) != "undefined"){
                        builder
                            .addTipEntry("X",commonFunctions.autoFormat(tipData.values[0]))
                            .addTipEntry("Y",commonFunctions.autoFormat(tipData.values[1]))
                            .addTipEntry("Z",commonFunctions.autoFormat(tipData.values[2]))
                    }
                    return builder.build();
                });
        }

        inverseHighlight.htmlBuilder = function(){

            var Builder = function(){
                this.html = '<div class="grid">';
                this.keyHtml = '  <div class="col">';
                this.valHtml = '  <div class="col">';


                //inverseHighlight.htmlBuilder.addTipEntry = addTipEntry;
                //inverseHighlight.htmlBuilder.build = build;
            }

            Builder.prototype.addTipEntry = function(key, val){
                if (typeof(val) != "undefined" && val != null) {
                    this.keyHtml += '<div>' + key + ':' + '</div>';
                    this.valHtml += '<div><span>' + val + '</span>' + '</div>';
                }
                return this;
            }

            Builder.prototype.build = function(){
                this.html += '' +
                    this.keyHtml +
                    '  </div>' +
                    this.valHtml +
                    '  </div>' +
                    '</div>';
                return this.html
            }

            return new Builder();
        }


        inverseHighlight.tip = function(tipObj){
            if (!arguments.length) {
                if (typeof(tip) == "undefined"){
                    tip = buildDefaultTip();
                }
                return tip;

            } else {
                tip = tipObj;
                return tip;
            }
        }

        /*
         * Changes the opacity of all elements matching cssSelector that descend from
         * node.  
         */
        var updateInverseHighlight = function(node, cssSelector) {
            var elements = node.selectAll(cssSelector);

            elements.each(function(d) {
                var e = d3.select(this).node();
                e.style.transition = "opacity 0.15s ease";
                if (typeof(highlight) == "undefined" || highlight === null || highlight === e) {
                    e.style.opacity = 1;
                } else {
                    e.style.opacity = 0.1;
                }
            });
        };

        /*
         * Builds a mouse move handler
         * seriesKey - the key for the series (e.g. "series 1"), added to the data object that d3-tip uses
         * 			   to display the tip text
         * dataTypes - the array of data types as passed from Stroom
         * node - the node the handler is attached to, e.g a parent of all the hoverable elements
         * cssSelector - the selector that will be used to find all the hoverable elements
         * tipNode - OPTIONAL The node to attach the tip to if you want it to always appear next to a static element
         */
        inverseHighlight.makeInverseHighlightMouseOverHandler = function(seriesKey, dataTypes, node, cssSelector, tipNode) {
            var tipData = {
                key: seriesKey,
                highlightedObj: null,
                types: dataTypes,
                values: []
            };
            var inverseHighlightMouseOverHandler  = function(d) {
                var eventTarget = this;
                var elements = node.selectAll(cssSelector);
                tipData.values = d;

                if (typeof(highlight) != "undefined") {
                    highlight = null;
                    if (highlight === null) {
                        elements.each(function(d) {
                            if (eventTarget === this || eventTarget.parentNode === this) {
                                tipData.highlightedObj = this;
                                highlight = this;
                            }
                        });
                    }
                    updateInverseHighlight(node, cssSelector);
                }

                if (typeof(tip) != "undefined") {
                    tip.attr('class', 'd3-tip animate');

                    if (typeof(tipNode) == "undefined"){
                        tip.show(tipData);
                    } else {
                        tip.show(tipData, tipNode);
                    }
                }
            };
            return inverseHighlightMouseOverHandler;
        }

        inverseHighlight.makeInverseHighlightMouseOutHandler = function(node, cssSelector) {
            var inverseHighlightMouseOutHandler = function() {
                highlight = null;
                updateInverseHighlight(node, cssSelector);
                if (typeof(tip) != "undefined") {
                    tip.attr('class', 'd3-tip');
                    tip.hide();
                }
            };
            return inverseHighlightMouseOutHandler;
        }

        return inverseHighlight;
    }


    commonFunctions.getScale = function(type, min, max) {
        var scale = {};
        if (type == "DATE_TIME") {
            scale.scale = d3.time.scale.utc().range([ min, max ]);
            scale.type = commonConstants.d3ScaleUtc;
        } else if (type == "NUMBER") {
            scale.scale = d3.scale.linear().range([ min, max ]);
            scale.type = commonConstants.d3ScaleLinear;
        } else {
            scale.scale = d3.scale.ordinal().rangeRoundBands([ min, max ], 0);
            scale.type = commonConstants.d3ScaleOrdinal;
        }
        return scale;
    };

    commonFunctions.createAxis = function(type, minPx, maxPx, d3TickFormat) {
        //console.log('createAxis called for type: ' + type + ' minPx: ' + minPx + ' maxPx: ' + maxPx);
        var result = {};
        var scaleObj = commonFunctions.getScale(type, minPx, maxPx);
        result.type = type;
        result.minPx = minPx;
        result.maxPx = maxPx;
        result.scale = scaleObj.scale;
        result.scaleType = scaleObj.type;
        result.axis = d3.svg.axis()
            .scale(result.scale)
            .tickSize(3);

        result.getValue = function(value) {
            if (value && !isNaN(value) && type == "DATE_TIME") {
                //value is a date time so return a date object for others to format as they wish
                var dateObj = new Date(value);
                return dateObj;
            }
            return value;
        };

        result.getMinValue = function() {
            return result.scale.domain()[0];
        }

        result.getMaxValue = function() {
            return result.scale.domain()[1];
        }

        result.getLengthPx = function() {
            return Math.max(result.minPx, result.maxPx) - Math.min(result.minPx, result.maxPx);
        }

        result.setExplicitDomain = function(valuesArr) {
            result.scale.domain(valuesArr);
        }

        result.setRangeDomain = function(dataType, data, i) {
            if (dataType == "DATE_TIME") {
                result.scale.domain([ data.min[i], data.max[i] ]);
            } else {
                //console.log([data.min[i], data.max[i]]);
                result.scale.domain([ 
                    Math.min(0, data.min[i]), 
                    Math.max(0, data.max[i]) 
                ]);
            }
        }

        result.setDomain = function(data, valuesArr, i) {
            if (data.types[i] == "GENERAL") {
                //some vis' may have computed unique values of ordinal data so try that first
                //otherwise fall back on the passed valuesArr
                if (data.visibleUnique && data.visibleUnique[i] && data.visibleUnique[i].length > 0){
                    var domain = data.visibleUnique[i];
                } else {
                    var domain = valuesArr.map(function(d) {
                        return d[i];
                    });
                }
                //var domain = valuesArr.map(function(d) {
                //return d[i];
                //});
                result.scale.domain(domain);
            } else if (data.types[i] == "DATE_TIME") {
                //console.log([ data.min[i], data.max[i] ]);
                result.scale.domain([ data.min[i], data.max[i] ]);
            } else {
                result.scale.domain([ Math.min(0, data.min[i]), Math.max(0, data.max[i]) ]);
            }
        };

        if (d3TickFormat) {
            result.tickFormat = d3TickFormat;
            result.axis.tickFormat(d3TickFormat);
        } else if (type == "DATE_TIME") {
            var format = d3.time.format.multi([ [ ".%L", function(d) {
                return d.getUTCMilliseconds();
            } ], [ ":%S", function(d) {
                return d.getUTCSeconds();
            } ], [ "%H:%M", function(d) {
                return d.getUTCMinutes();
            } ], [ "%H:%M", function(d) {
                return d.getUTCHours();
            } ], [ "%b %d", function(d) {
                return d.getUTCDate();
            } ], [ "%b %Y", function(d) {
                return d.getUTCMonth();
            } ], [ "%Y", function() {
                return true;
            } ] ]);

            result.format = format;
            result.axis.tickFormat(format);
        } else if (!isNaN(result.getMaxValue()) && result.getMaxValue() > 100000) {
            result.format = d3.format("s");
            result.axis.tickFormat(result.format);
        }

        return result;
    };

    commonFunctions.setColourDomain = function(scale, data, i, mode) {
        //console.log("Setting domain - " + data.key + " - " + colour.domain());
        var domain;

        if (mode == "VALUE" && data.unique) {
            domain = data.unique[i];
        } else if (mode == "SYNCHED_SERIES" && data.uniqueKeys) {
            domain = data.uniqueKeys;
        } else if (data.originalValues) {
            //attempt to use all the series for the colour scale, not just the visible ones
            domain = data.originalValues.map(function(d) {
                return d.key;
            });
        } else if (data.values && data.values[0].hasOwnProperty("key")) {
            domain = data.values.map(function(d) {
                return d.key;
            });
        } else {
            domain = data.values.map(function(d) {
                return d[i];
            });
        }
        //TODO Need more thought into how we set the colour domain when new data is being added to an
        //existing vis.  The current approach means an existing series may change colour.  This may be what you want
        //if the series are ordered but if you you probably want to add the new items to the end of the domain array
        //so the existing keys keep their colour
        scale.domain([]);
        scale.domain(domain);
        //console.log("Setting domain - " + data.key + " - " + scale.domain());
    };

    commonFunctions.flattenGroupedValues = function(data) {
        var flatData = [];
        if (data.values){
            data.values.forEach(function(val) {
                flatData.concat(val.values);
            });
        }
        return flatData;
    };

    /*
     * Looks at the size of the two axes containers and resizes the margins to suit
     * Returns true if the margins have been changed
     */
    commonFunctions.resizeMargins = function(margins, xAxisContainer, yAxisContainer) {

        var xAxisBox = xAxisContainer.node().getBBox();
        var yAxisBox = yAxisContainer.node().getBBox();
        //console.log("xw: " + xAxisBox.width + " xh: " + xAxisBox.height + " yw: " + yAxisBox.width + " yh: " + yAxisBox.height);
        var xAxisHeight = Math.max(15, xAxisBox.height + 2);
        var yAxisWidth = Math.max(15, yAxisBox.width + 2);


        if (margins.bottom != xAxisHeight || margins.left != yAxisWidth) {
            margins.bottom = xAxisHeight;
            margins.left = yAxisWidth;
            //console.log("yAxisBox.width: " + yAxisBox.width + " yAxisWidth: " + yAxisWidth + " old-margins.left: " + margins.left + " true " + margins.gridKey);
            return true;
        } else {
            //console.log("yAxisBox.width: " + yAxisBox.width + " yAxisWidth: " + yAxisWidth + " old-margins.left: " + margins.left + " false " + margins.gridKey);
            return false;
        }
    };

    commonFunctions.resize = function(grid, updateFunc, element, margins, width, height){
        if (grid) {
            grid.resize();
        } else {
            var newWidth = element.clientWidth - margins.right - margins.left;
            var newHeight = element.clientHeight - margins.top - margins.bottom;
            if (newWidth != width || newHeight != height) {
                //need to do the update with trasition time of zero otherwise we getr all sorts of
                //interpolation errors in the setting of the svg translate attribute by D3
                updateFunc(0);
            }
        }
    };

    commonFunctions.getBucketValues = function(minVal, maxVal, bucketCount) {

        // don't round it at this point otherwise we will get rounding errors when
        // we cumulatively add bucket sizes
        var bucketSize = (maxVal - minVal) / bucketCount;
        var bucketValues = [];
        var cumulativeVal = minVal;

        for (var i = 0; i < bucketCount; i++) {
            cumulativeVal += bucketSize;
            bucketValues[i] = cumulativeVal;
        }
        // console.log("bucketValues: " + bucketValues);
        return bucketValues;
    };

    //Builds an array of data points for a heat map legend.
    //bucketValues - An array of 
    commonFunctions.buildHeatMapLegendData = function(bucketValues, heatMapColours) {
        var legendData = [];
        var coloursArr;
        if (typeof heatMapColours == 'undefined'){
            coloursArr = commonConstants.heatMapColours;
        } else {
            coloursArr = heatMapColours;
        }

        for (var i = 0; i < bucketValues.length; i++) {
            // console.log("bucketValues[" + i + "]: " + bucketValues[i]);
            var legendDataPoint = {
                bucket : i,
                thresholdValue : bucketValues[i],
                rgbValue : coloursArr[i]
            };

            // console.log("legendDataPoint: " + legendDataPoint.bucket + " " +
            // legendDataPoint.thresholdValue + " " + legendDataPoint.rgbValue);
            legendData[i] = legendDataPoint;
        }
        return legendData;
    };

    /*
     * if val is greater than an SI unit then divide it by that unit and truncate
     * to desired number of decimal places. e.g. 66123 => 66.123k, 74657303 =>
     * 74.657M
     */
    commonFunctions.toSiUnits = function(val, decimalPlaces) {
        var result = "";
        var kilo = 1000;
        var mega = kilo * 1000;
        var giga = mega * 1000;
        var tera = giga * 1000;
        var peta = tera * 1000;

        val = commonFunctions.toVariablePrecision(val, decimalPlaces);

        if (val < kilo) {
            result = commonFunctions.toVariablePrecision(val, decimalPlaces);
        } else if (val >= kilo && val < mega) {
            result = commonFunctions.toVariablePrecision((val / kilo), decimalPlaces)
                + " k";
        } else if (val >= mega && val < giga) {
            result = commonFunctions.toVariablePrecision((val / mega), decimalPlaces)
                + " M";
        } else if (val >= giga && val < tera) {
            result = commonFunctions.toVariablePrecision((val / giga), decimalPlaces)
                + " G";
        } else if (val >= tera && val < peta) {
            result = commonFunctions.toVariablePrecision((val / tera), decimalPlaces)
                + " M";
        } else if (val >= peta) {
            result = commonFunctions.toVariablePrecision((val / peta), decimalPlaces)
                + " P";
        }
        return result;
    };

    // rounds a number to at most the number of decimal places provided
    commonFunctions.toVariablePrecision = function(value, maxPrecision) {

        precisionVal = Math.pow(10, maxPrecision);
        inversePrecisionVal = Math.pow(10, (-1 * (maxPrecision + 2)));

        return Math.round((value + inversePrecisionVal) * precisionVal)
            / precisionVal;
    };


    commonFunctions.commaFormatted = function(amount) {
        var delimiter = ","; // replace comma if desired
        var amount = new String(amount);
        var a = amount.split('.',2);
        if (a.length > 1){
            var d = a[1];
        }
        var i = parseInt(a[0]);
        if(isNaN(i)) { return ''; }
        var minus = '';
        if(i < 0) { minus = '-'; }
        i = Math.abs(i);
        var n = new String(i);
        var a = [];
        while(n.length > 3)
        {
            var nn = n.substr(n.length-3);
            a.unshift(nn);
            n = n.substr(0,n.length-3);
        }
        if(n.length > 0) { a.unshift(n); }
        n = a.join(delimiter);
        if(typeof(d) == "undefined") { amount = n; }
        else { amount = n + '.' + d; }
        amount = minus + amount;
        return amount;
    };

    //Formats a date object, time in MS or a valid javascript date string
    //using the passed D3 date format string
    commonFunctions.dateToStr = function(date, formatStr) {
        if (typeof(formatStr) === "string") {
            var format = d3.time.format(formatStr);
        } else {
            //invalid formatStr so return iso format
            var format = d3.time.format.iso;
        }
        if (date instanceof Date) {
            return format(date);
        } else {
            return format(new Date(date));
        }
    };

    commonFunctions.autoFormat = function(val, dateFormat){
        //console.log("Value: " + val + " type: " + typeof(val));
        if (dateFormat) {
            return commonFunctions.dateToStr(val, dateFormat);
        } else if (val instanceof Date) {
            return val.toString();
        } else if (typeof(val) == "number"){
            return commonFunctions.commaFormatted(commonFunctions.toVariablePrecision(val,3));
        } else {
            return val;
        }
    };

    commonFunctions.pad = function(str, max) {
        str = str.toString();
        return str.length < max ? commonFunctions.pad("0" + str, max) : str;
    };
    
    commonFunctions.capitalise = function(str) {
        return str.charAt(0).toUpperCase() + str.slice(1);
    };

    // work out day of the week of the passed date, returns 0-6, 0==Mon
    commonFunctions.getDayNoOfWeek = function(dateObj) {

        var dayOfWeekNo = dateObj.getDay() - 1;
        if (dayOfWeekNo == -1) {
            dayOfWeekNo = 6;
        }
        return dayOfWeekNo;
    };

    commonFunctions.getDaysBetweenInclusive = function(minTimeMs, maxTimeMs) {
        var startDayNo = Math.floor(minTimeMs / commonConstants.millisInDay);
        var endDayNo = Math.floor(maxTimeMs / commonConstants.millisInDay);
        return endDayNo - startDayNo + 1;
    }

    commonFunctions.getTwoDigitHourOfDay = function(timeMs) {
        return commonFunctions.pad(new Date(timeMs).getHours(), 2);
    };

    // returns a date object representing 00:00:00 on the last Monday before the
    // passed date
    commonFunctions.getWeekCommencingDate = function(dateObj) {
        // subtract a number of days to get us back to a time on a Monday then
        // truncate that time to the start of the day to give us the week commencing
        // time
        return new Date(
            Math
            .floor((dateObj.getTime() - (commonConstants.millisInDay * commonFunctions
            .getDayNoOfWeek(dateObj)))
            / commonConstants.millisInDay)
            * commonConstants.millisInDay);
    };

    commonFunctions.truncateToStartOfInterval = function(timeMs, intervalMs) {
        return Math.floor(timeMs / intervalMs) * intervalMs;
    }

    commonFunctions.truncateToStartOfDay = function(timeMs) {
        return commonFunctions.truncateToStartOfInterval(timeMs, commonConstants.millisInDay);
    };

    commonFunctions.millisSinceStartOfDay = function(timeMs) {
        return timeMs - commonFunctions.truncateToStartOfDay(timeMs);
    };

    commonFunctions.generateContiguousTimeArray = function(minTimeMs, maxTimeMs,
        intervalMs, conversionFunc) {

        //console.log("min: " + new Date(minTimeMs) + " max: " + new Date(maxTimeMs));

        if (intervalMs === commonConstants.millisInWeek) {
            //special case for week intervals as we want them to start on a Monday
            //Unix epoch is a Thursday so simple rounding is no good
            var timeMs = commonFunctions.getWeekCommencingDate(new Date(minTimeMs)).getTime();
        } else {
            var timeMs = commonFunctions.truncateToStartOfInterval(minTimeMs, intervalMs);
        }

        var arr = [];

        while (timeMs <= maxTimeMs) {
            arr.push(conversionFunc(timeMs));
            timeMs += intervalMs;
        }
        return arr;
    };

    /*
     * Adds a label for showing the values of a data point in a visualisation.
     * Label is added relative to the current cursor position
     * 
     * rootElement - The root element passed to the visualisation by GWT xPos -
     * The x position you want the label to work from yPos - The y position you
     * want the label to work from m - The margin array seriesName - The name of
     * the series of the data point, or null if not applicable dimensionXVal - The
     * value of the x dimension of the data point, or null if not applicable
     * dimensionYVal - The value of the y dimension of the data point, or null if
     * not applicable
     */
    commonFunctions.addDataPointLabel = function(rootElement, m, seriesName,
        dimensionXVal, dimensionYVal) {

        var topDelta = 13;

        // Add a label to show information relating to the data point
        var label = d3.select(rootElement).select("div.vis-label");
        if (label.node() === null) {
            var topVal = 0;

            label = d3.select(rootElement).append("div").attr("class", "vis-label")
                .style("background-color", "#eeeeee");

            if (seriesName !== null && seriesName !== "") {
                var labelName = label.append("div").attr("class",
                    "vis-seriesLabel name").style("top", topVal + "px").style(
                        "background-color", "inherit");
                        topVal += topDelta;
            }

            if (dimensionXVal !== null) {
                var labelX = label.append("div").attr("class", "vis-seriesLabel x")
                    .style("top", topVal + "px").style("background-color", "inherit");
                topVal += topDelta;
            }

            if (dimensionYVal !== null) {
                var labelY = label.append("div").attr("class", "vis-seriesLabel y")
                    .style("top", topVal + "px").style("background-color", "inherit");
                topVal += topDelta;
            }

            label.style("opacity", "1");
        }

        var px = d3.event.pageX;
        var py = d3.event.pageY;

        var xPadding = 12;
        var yPadding = 7;
        var lblWidth = label.node().scrollWidth;
        var lblHeight = label.node().scrollHeight;
        var lblLeft = px + xPadding;
        var lblRight = lblLeft + lblWidth;
        var lblTop = py - yPadding;
        var lblBottom = lblTop + lblHeight;

        if (lblRight > rootElement.clientWidth
            && lblLeft - lblWidth - (2 * xPadding) > m[3] + 2) {
                lblLeft = lblLeft - lblWidth - (2 * xPadding);
            }
            if (lblBottom + 2 > rootElement.clientHeight - m[2]
                && lblTop - lblHeight + (2 * yPadding) > 0) {
                    lblTop = lblTop - lblHeight + (2 * yPadding);
                }

                label.style("opacity", "1").style("left", lblLeft + "px").style("top",
                    lblTop + "px");

                    if (seriesName !== null && seriesName !== "") {
                        label.select("div.name").text(seriesName);
                    }

                    if (dimensionXVal !== null) {
                        label.select("div.x").text(dimensionXVal);
                    }

                    if (dimensionYVal !== null) {
                        label.select("div.y").text(dimensionYVal);
                    }
    };

    /*
     * isZoomedIn - boolean to stored the zoomed in state
     * allData - Object containing all data passed from the query
     * visibleData - Object containing a subset of the data passed from the search query, ie. just for the zoomed in series
     * transitionDuration - time in MS to use for any D3 transitions
     * updateCallback - The function the handler should call to update the visualistaion after the zoom state change
     */
    commonFunctions.seriesZoomMouseHandler = function (isZoomedIn, allData, transitionDuration, updateCallback) {

        var handler = function (d) {
            if (isZoomedIn) {
                //Zooming OUT so set visibleData back to allData
                isZoomedIn = false;
                //console.log("zooming out");
                //visibleData = allData;
                updateCallback(transitionDuration, allData);
            } else {
                //Zooming IN so truncate allData down to one series
                //console.log("zooming in");
                isZoomedIn = true;

                var visibleData = {
                    max: allData.max,
                    min: allData.min,
                    sum: allData.sum,
                    types: allData.types,
                    values: [d],
                    seriesCount: 1
                };
                updateCallback(transitionDuration, visibleData);
            }
        }
        return handler;
    }

    commonFunctions.zoomIconTextFunc = function(isZoomedIn) {
        if (isZoomedIn) {
            return commonConstants.fontAwesomeCompress;
        } else {
            return commonConstants.fontAwesomeExpand;
        }
    }

    commonFunctions.gridAwareWidthFunc = function(subtractMargins, containerNode, element, margins) {
        var effectiveMargins;
        if (margins){
            effectiveMargins = margins;
        } else {
            effectiveMargins = commonConstants.margins();
        }

        var padding = subtractMargins ? effectiveMargins.right + effectiveMargins.left : 0;

        if (containerNode){
            //return containerNode.getAttributeNS(null,'width') - padding;
            return Math.max(containerNode.width.baseVal.value - padding,0);
        } else {
            return Math.max(element.clientWidth - padding,0);
        }
    }

    commonFunctions.gridAwareHeightFunc = function(subtractMargins, containerNode, element, margins) {
        var effectiveMargins;
        if (margins){
            effectiveMargins = margins;
        } else {
            effectiveMargins = commonConstants.margins();
        }
        var padding = subtractMargins ? effectiveMargins.top + effectiveMargins.bottom : 0;

        if (containerNode){
            //return containerNode.getAttributeNS(null,'height') - padding;
            return Math.max(containerNode.height.baseVal.value - padding,0);
        } else {
            return Math.max(element.clientHeight - padding,0);
        }
    }

    /*
     * Appends a div element to the passed element.  If the passed element is
     * an svg node then it create it under a foreignObject element
     *
     * NOTE - I have had some issues with the content not displaying correctly
     * in chrome, so this is still work in progress.  Some web sites suggest
     * the html needs to live inside a body element but mbostock seems to use
     * a div as the top html element.  I beleive the namespace of the top level 
     * element inside the FO needs to be declared as xhtml so it knows what it
     * is dealing with.
     */
    commonFunctions.addDiv = function(element, classValue) {
        var div;
        if (element.localName == "svg"){
            var body = d3.select(element)
                .append("svg:foreignObject")
            //.attr("width","100%")
            //.attr("height","100%")
            //.attr("requiredExtensions","http://www.w3.org/1999/xhtml")
                .append("xhtml:body")
            //.attr("class", classValue);
            //.attr("xmlns","http://www.w3.org/1999/xhtml")

            div = body
                .append("xhtml:div")
                .attr("class", classValue);
        } else {
            div = d3.select(element)
                .append("div")
                .attr("class", classValue);
        }
        return div;
    }

    /*
     * Takes a standard Stroom data object and removes the top level of nesting,
     * copying the types property from the removed top level down to returned
     * child.  Only the first child value is returned
     */
    commonFunctions.unNestData = function(data) {

        if (!data) {
            return data;
        }

        var unNestedData = data.values[0];
        unNestedData.types = data.types;

        return unNestedData;
    }

    //Pass in a data tree and an array of field positions types and it  will add a 'unique' property at each nest level
    //containing a sparse array (one value per ordinal field) with each value being an array
    //of the unique values for that field at that level.  Similar to the way min/max/sum work
    //in the data tree.  The optional filter func arg allows you to pass a function that will filter on the type and/or field position
    //to determine which fields to process.
    commonFunctions.computeUniqueValues = function(data, typeAndFieldIndexFilterFunc) {

        var makeAddUniqueValueFunc = function(fieldIndex) {
            var fieldIndex = fieldIndex;
            //recursive function to walk the data tree computing the unique values of the given 
            //fieldIndex at each level
            var addUniqueValues = function(obj){
                if (!obj.hasOwnProperty("unique")) {
                    obj.unique = [];
                }
                if (!obj.hasOwnProperty("visibleUnique")) {
                    obj.visibleUnique = [];
                }
                //TODO the problem with using maps is that we lose the original order of the data.
                //Probably need to add an arg to the outer function to give a choice of sroting or preserving
                //the original order
                var valuesMap = {};
                var visibleValuesMap = {};
                if (obj.values && obj.values.length > 0) {
                    if (obj.values[0].hasOwnProperty('min')){
                        //This obj has another level of nesting under it so process the level below
                        obj.values.forEach(makeAddUniqueValueFunc(fieldIndex));

                        //now compute the unique values for this level based on the unique values of the level below.
                        obj.values.forEach(function(childObj){
                            childObj.unique[fieldIndex].forEach(function(uniqueVal){
                                valuesMap[uniqueVal] = 1;
                            })
                        });
                        obj.visibleValues().forEach(function(childObj){
                            childObj.visibleUnique[fieldIndex].forEach(function(uniqueVal){
                                visibleValuesMap[uniqueVal] = 1;
                            })
                        });
                    } else {
                        //This is the last object in this branch of the tree
                        //Each item in the 'values' array is an array of point values
                        //Therefore need to compute the unique values for this level
                        obj.values.forEach(function(valueData) {
                            valuesMap[valueData[fieldIndex]] = 1;
                        });
                        obj.visibleValues().forEach(function(valueData) {
                            visibleValuesMap[valueData[fieldIndex]] = 1;
                        });
                    }
                }
                //Need to sort the unique values else they end up coming out in an inconsistent order
                obj.unique[fieldIndex] = d3.keys(valuesMap);
                obj.unique[fieldIndex].sort();
                obj.visibleUnique[fieldIndex] = d3.keys(visibleValuesMap);
                obj.visibleUnique[fieldIndex].sort();
            };
            return addUniqueValues;
        };

        //use the types array to loop round so we process each field,
        //optionally filtering whether we process the field or not
        data.types.forEach(function(type, fieldIndex) {
            if (typeof(typeAndFieldIndexFilterFunc) === "undefined" || typeAndFieldIndexFilterFunc(type, fieldIndex)) {
                makeAddUniqueValueFunc(fieldIndex)(data);
            }
        });
    }

    //returns an array of unique 'key' values for the second level of nesting in the 
    //passed data tree.
    commonFunctions.computeUniqueKeys = function(data) {
        //make sure we have a key attribute for the first grand child
        if (data && data.values && data.values.length > 0 && data.values[0] && 
            data.values[0].values && data.values[0].values.length > 0 && 
            data.values[0].values[0] && data.values[0].values[0].key) {

            var keyMap = {};
            var visibleKeyMap = {};
            data.values.forEach(function(childObj){
                childObj.values.forEach(function(grandChildObj){

                    keyMap[grandChildObj.key] = 1;

                    //now do the same for the next level down from our original starting point
                    //i.e. we are always finding keys from 2 levels below where we are
                    commonFunctions.computeUniqueKeys(childObj);
                });
            });
            data.visibleValues().forEach(function(childObj){
                childObj.visibleValues().forEach(function(grandChildObj){

                    visibleKeyMap[grandChildObj.key] = 1;

                    //now do the same for the next level down from our original starting point
                    //i.e. we are always finding keys from 2 levels below where we are
                    commonFunctions.computeUniqueKeys(childObj);
                });
            });

            //Need to sort the unique values else they end up coming out in an inconsistent order
            //TODO if the dashbaord settings are for unsorted series then we should probably put the
            //keys in the order in which they came, however we are then trying to merge the keys from a
            //number of grid cells each of which might be in a different order, thus it may not be possible
            data.uniqueKeys = d3.keys(keyMap);
            data.uniqueKeys.sort();
            data.visibleUniqueKeys = d3.keys(visibleKeyMap);
            data.visibleUniqueKeys.sort();
        }
    };


    //Re-compute the min/max/sum aggregates for the data at this level or below
    //using either the raw values or lower level aggregates
    //Aggregates are based on visible values only as they are used to deinfe the
    //axes
    commonFunctions.dataAggregator = function() {

        var aggregator = {
            _isRecursive : true,
            _useVisibleValues : true
        };

        var computeAggregates = function(data, isRecursive, useVisibleValues) {
            var values = useVisibleValues ? data.visibleValues() : data.values;

            if (values && values.length > 0){
                //var visibleValues = data.visibleValues();
                if (values[0].hasOwnProperty("key") && data.min){
                    //is a branch so compute aggregates based on aggregates of children

                    //If we are recursing, first ensure we get the childen to work out their aggregates
                    if (isRecursive) {
                        values.forEach(function(childVal) {
                            computeAggregates(childVal, isRecursive, useVisibleValues);
                        });
                    }
                    //Use min to give us the value array positions
                    data.min.forEach(function(d,i) {
                        data.min[i] = d3.min(values, function(val) {return val.min[i];});
                        data.max[i] = d3.max(values, function(val) {return val.max[i];});
                        data.sum[i] = d3.sum(values, function(val) {return val.sum[i];});
                    });
                } else {
                    //is the last branch so compute aggregates based on child values
                    //Use min to give us the value array positions
                    //var valFunc = ;
                    data.min.forEach(function(d,i) {
                        data.min[i] = d3.min(values, function(val) {return val[i];});
                        data.max[i] = d3.max(values, function(val) {return val[i];});
                        data.sum[i] = d3.sum(values, function(val) {return val[i];});
                    });
                }
            }
        };

        aggregator.setRecursive = function(val) {
            if (typeof(val) === "undefined") {
                return this._isRecursive;
            } else {
                this._isRecursive = val;
                return aggregator;
            }
        };

        aggregator.setUseVisibleValues = function(val) {
            if (typeof(val) === "undefined") {
                return this._useVisibleValues;
            } else {
                this._useVisibleValues = val;
                return aggregator;
            }
        };

        aggregator.aggregate = function(data) {
            computeAggregates(data, aggregator._isRecursive, aggregator._useVisibleValues);
        }

        return aggregator;
    };

    //Recursive function to walk the data tree looking for any branches with 
    //missing key properties, adding .key=ErrorMissingKey
    commonFunctions.cleanMissingKeys = function(data) {
        if (data.values) {
            data.values.forEach(function (value) {
                if (value.hasOwnProperty("min")) {
                    //This is a branch so we should have a key here
                    if (!value.hasOwnProperty("key")) {
                        //Missing key property so create one
                        if (data.values.length === 1) {
                            value.key = "Series";
                        } else {
                            value.key = "ErrorMissingKey";
                        }
                    }
                    commonFunctions.cleanMissingKeys(value);
                } else {
                    //This is a leaf so do nothing
                }
            });
        }
    };

    //function to add a block of html to display a legend to the passed parentNode
    //using the colours and domain values in the passed d3 ordinal colour scale (e.g.
    //d3.scale.category20() )
    //If there are no domain values then no html will be added.
    //If includeMouseEvents==true then hover events will be added to each colour block in
    //the legend to 'hide' the vis elements of all other colours.  This requires the vis
    //to append the classes vis-coloured-element and vis-legend-series-XXXXXX where XXXXXX is a 
    //hash of the series name.  This saves having to look for different style properties
    //like fill/background-colour/etc. to look for the colour
    commonFunctions.buildLegend = function(
        parentNode, 
        colourScale, 
        includeMouseEvents, 
        maxWidth, 
        maxHeight, 
        legendStateMap, 
        setDataFunc,
        keyTextFormatFunc){

        if (typeof(colourScale) != "undefined" && colourScale.domain().length > 0) {

            var outer = d3.select(parentNode).node()
                .append("div")
                .classed("d3-tip", true)
                .classed("legend", true);

            //limit the size of the legend based on the size of the cell
            var maxLegendWidth = Math.floor(maxWidth * 0.5);
            var maxLegendHeight = Math.floor(maxHeight * 0.5);

            var scrollableDiv = outer
                .append("div")
                .classed("legend-scrollable-container", true)
                .style("max-width", maxLegendWidth + "px")
                .style("max-height", maxLegendHeight + "px");

            var grid = scrollableDiv
                .append("div")
                .classed("legend-inner-container", true)

            //loop round all vlaues in the colour scale domain and add a legend entry for it
            colourScale.domain().forEach(function(domainValue) { 
                var colourVal = colourScale(domainValue);
                var legendState = legendStateMap.get(domainValue);
                //convert the series name to a hash to avoid any chars in the series name
                //from causing problems when it is used in a css class name
                var seriesKeyHash = commonFunctions.generateHash(domainValue);
                var hashedKeyClass = "vis-legend-key-" + seriesKeyHash;
                //console.log("domainValue: " + domainValue + " seriesKeyHash: " + seriesKeyHash);


                //function to construct an event listener function
                var legendColourMouseHandler = function(isActive) {
                    return function(d, i) {
                        var classToExclude = hashedKeyClass;
                        //console.log("domainValue: " + domainValue + " seriesKeyHash: " + seriesKeyHash);

                        //find all coloured elements apart from the colour we are hovering over
                        var otherColouredElements = d3.selectAll(".vis-coloured-element:not(." + classToExclude + ")");

                        otherColouredElements.each(function(d,i) {
                            var otherElm = d3.select(this).node();
                            otherElm.style.transition = "opacity 0.25s ease";

                            if (isActive) {
                                otherElm.style.opacity = 0.05;
                            } else {
                                otherElm.style.opacity = 1;
                            }
                        });
                    };
                };

                var rowDiv = grid
                    .append("div")
                    .classed("legend-row",true);

                var keyDiv = rowDiv
                    .append("div")
                    .classed("legend-key-container",true)
                    .append("div")
                    .classed("legend-key",true)
                    .classed(hashedKeyClass ,true)
                    .style("border-color", colourVal);

                if (legendState.isVisible) {
                    keyDiv.style("background-color", colourVal);
                }

                var mouseDownHandler = function(d, i) {

                    var keyDiv = d3.select(this);
                    if (legendState.isVisible) {
                        keyDiv.style("background-color", colourVal);
                    } else {
                        keyDiv.style("background-color", null);
                    }

                    if (!legendState.isVisible || (legendState.isVisible && legendStateMap.getCountByState(true) > 1)) {
                        legendState.toggleState();
                    }
                    setDataFunc();
                    //clear the mouse over behaviour
                    legendColourMouseHandler(false)(d,i);
                };

                if (includeMouseEvents) {
                    keyDiv
                        .on("mouseover", legendColourMouseHandler(true))
                        .on("mouseout", legendColourMouseHandler(false))
                        .on("mousedown", mouseDownHandler);
                }

                if (keyTextFormatFunc) {
                    var keyText = keyTextFormatFunc(domainValue);
                } else {
                    var keyText = domainValue;
                }

                rowDiv
                    .append("div")
                    .classed("legend-text",true)
                    .append("span")
                    .text(keyText);
            });
        }
    };

    commonFunctions.removeColourHash = function(colourHex){
        if (typeof(colourHex) === "string" && colourHex){
            return colourHex.replace('#','').toLowerCase();
        } else {
            return colourHex;
        }
    };

    commonFunctions.generateHash = function(str) {
        if (typeof(str) ==="number") {
            var plainText = str.toString();
        } else if (!str || typeof(str) != "string"){
            var plainText = "";
        } else {
            var plainText = str;
        }
        var base64 = new Hashes.Base64;
        //Use a custom table for the base64 encoding as we want to ensure it works in a css selector
        //and also we don't ever need to decode it.
        base64.setTab("ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789");
        base64.setPad("-");
        return base64.encode(plainText);
    };

    commonFunctions.makeColouredElementClassStringFunc = function(keyFunc, additionalClasses, keyToHashedKeyMap){

        return function(d,i) {
            var classStr = "";
            if (typeof(additionalClasses) === "string" && additionalClasses){
                classStr += additionalClasses;
            }

            //try and get the hash from a map of previously hashed keys to
            //save on processing
            var key = keyFunc(d,i);
            var hashedKey;

            if (typeof keyToHashedKeyMap != "undefined"){
                hashedKey = keyToHashedKeyMap[key];
                if (typeof hashedKey == "undefined"){
                    hashedKey = commonFunctions.generateHash(key);
                    keyToHashedKeyMap[key] = hashedKey;
                }
            } else {
                hashedKey = commonFunctions.generateHash(key);
            }

            classStr += " vis-coloured-element vis-legend-key-" + hashedKey;
            return classStr;
        };
    };

    commonFunctions.decodeTimePeriod = function(timePeriod){
        var period = timePeriod.toLowerCase();
        if (period === "second"){
            return commonConstants.millisInSecond;
        } else if (period === "minute") {
            return commonConstants.millisInMinute;
        } else if (period === "hour") {
            return commonConstants.millisInHour;
        } else if (period === "day") {
            return commonConstants.millisInDay;
        } else if (period === "week") {
            return commonConstants.millisInWeek;
        } else {
            return 0;
        }
    };

    //determine the number of ticks to use on a 24hr time axis given an axis length
    commonFunctions.getHourAxisTickMarkCount = function(axisLength){
        var xTicks;
        if (axisLength < 200){
            xTicks = 6;
        } else if (axisLength < 400) {
            xTicks = 12;
        } else {
            xTicks = 24;
        }
        return xTicks;
    };

    //Finds the closest point on a path to the passed x and y coordinates
    //Copy of mbostock's Closest Point on Path code
    commonFunctions.closestPoint = function(pathNode, mouseX, mouseY) {
        var pathLength = pathNode.getTotalLength();
        var precision = 16;
        var best;
        var bestLength;
        var bestDistance = Infinity;

        //linear scan for coarse approximation
        for (var scan, scanLength = 0, scanDistance; scanLength <= pathLength; scanLength += precision) {
            if ((scanDistance = distance2(scan = pathNode.getPointAtLength(scanLength))) < bestDistance) {
                best = scan;
                bestLength = scanLength;
                bestDistance = scanDistance;
            }
        }

        //binary search for precise estimate
        precision /= 2;
        while (precision > 2) {
            var before;
            var after;
            var beforeLength;
            var afterLength;
            var beforeDistance;
            var afterDistance;

            if ((beforeLength = bestLength - precision) >= 0 && (beforeDistance = distance2(before = pathNode.getPointAtLength(beforeLength))) < bestDistance) {
                best = before;
                bestLength = beforeLength;
                bestDistance = beforeDistance;
            } else if ((afterLength = bestLength + precision) <= pathLength && (afterDistance = distance2(after = pathNode.getPointAtLength(afterLength))) < bestDistance) {
                best = after;
                bestLength = afterLength;
                bestDistance = afterDistance;
            } else {
                precision /= 2;
            }
        }

        best.distance = Math.sqrt(bestDistance);
        return best;

        function distance2(p) {
            var dx = p.x - mouseX;
            var dy = p.y - mouseY;
            return dx * dx + dy * dy;
        }
    };

    //Loop through all the passed paths for each one establish the closest point
    //so that the closest point of all paths can be found
    //paths - the list of paths (d3 nodes) that you want to include
    //when looking for the closest
    commonFunctions.closestPath = function(mouseX, mouseY, paths) { 
        var closestPath;
        var closestPos = {};
        var closestKey;
        var closestData;
        var smallestDistToPath = -1;

        paths.each(function(d) {
            var path = d3.select(this);
            var pathEl = path.node();

            var closestPointOnPath = commonFunctions.closestPoint(pathEl, mouseX, mouseY);

            if (smallestDistToPath === -1 || closestPointOnPath.distance < smallestDistToPath) {
                //the closest point of this path is closer than the closest path so far so
                //update our closest path
                smallestDistToPath = closestPointOnPath.distance;
                closestPath = path;
                closestPos.x = closestPointOnPath.x;
                closestPos.y = closestPointOnPath.y;
                closestKey = d.key;
                closestData = d;
            }
        });

        return {
            closestPath: closestPath,
            closestPos: closestPos,
            closestKey: closestKey,
            closestData: closestData
        }
    };

    //cache to hold mappings from raw text to the truncated form as the truncation process
    //is quite costly
    commonFunctions.truncateTextCache = {};

    //Sets the text on the node of d3.select(this), but if the text is longer than width, 
    //truncates it and ends it with '...'
    //e.g. selectAll(".myTextElementClassName").each(commonFunctions.truncateText(80));
    commonFunctions.truncateText = function(width, lengthFunc) {
        //console.log('Creating func with width ' + width);

        //TODO need to rethink this as each we seem to get lots of different width varients building up in the cache
        //for a gridded vis
        if (!commonFunctions.truncateTextCache.hasOwnProperty(width)){
            commonFunctions.truncateTextCache[width] = {};
        }
        var textMap = commonFunctions.truncateTextCache[width];

        return function() {
            var self = d3.select(this);
            var originalText = self.text();
            var truncatedText = originalText;

            if (textMap.hasOwnProperty(originalText)) {
                //already truncated this one so pull the truncated form from the map
                truncatedText = textMap[originalText];
                self.text(truncatedText);
                //console.log(width + ' pulling [' + originalText + '] from cache with value [' + truncatedText + ']');
            } else {
                //new to us so perform the truncation and cache it for later
                var textLength = lengthFunc(self.node());
                var padding = 0;
                var currentText ;
                var currentText = truncatedText;

                while (textLength > (width - 2 * padding) && truncatedText.length > 0) {
                    truncatedText = truncatedText.slice(0, -1);
                    currentText = truncatedText + '...';
                    self.text(currentText);
                    textLength = lengthFunc(self.node());
                }
                textMap[originalText] = currentText;


                //console.log(width + ' truncating [' + originalText + '] to [' + currentText + ']');
            }
            if (self.text() !== originalText){
                //console.log(currentText + ' - ' + originalText);
                self.classed("truncated", true);
            }
        }
    };

    commonFunctions.buildAxis = function(axisContainer, axisSettings, orientation, ticks, maxAxisLabelLengthPx, displayText){

        var axisLabelTip = d3.tip()
            .direction(commonFunctions.d3TipDirectionFunc)
            .attr('class', 'd3-tip')
            .html(commonFunctions.makeD3TipBasicTextHtmlFunc(function(val) {
                if (axisSettings.tickFormat) {
                    return axisSettings.tickFormat(val);
                } else {
                    return val;
                }
            }));

        var axis = axisSettings.axis
            .orient(orientation);

        var isTextDisplayed = commonFunctions.isTrue(displayText);

        if (ticks) {
            axis.ticks(ticks);
        } else {
            //work out how many ticks to have based on axis pixel length
            var pixelsPerTick = (orientation == "bottom" ? 45 : 20);
            var minimumPixelsPerTick = (orientation == "bottom" ? 20 : 10);
            var optimumTicks = Math.floor(axisSettings.getLengthPx() / pixelsPerTick);
            if (optimumTicks <= 1 ) {
                var minTicks = Math.floor(axisSettings.getLengthPx() / minimumPixelsPerTick);
                axis.ticks(minTicks);
            } else {
                axis.ticks(optimumTicks);
            }
            //console.log('lengthPx: ' + axisSettings.getLengthPx() + ' optimumTicks: ' + optimumTicks);
        }

        if (!isTextDisplayed) {
            axis.tickFormat("");
        } else {
            axis.tickFormat(axisSettings.tickFormat);
        }

        if (axisSettings.scaleType === commonConstants.d3ScaleOrdinal){
            //ordinal axis so truncate text if too long
            var optimumLabelLengthPx = Math.floor(Math.min(axisSettings.getLengthPx() * 0.2, 200));


            axisContainer
                .call(axis)
                //.call(axisSettings.axis.orient(orientation))
                .call(axisLabelTip);

            if (isTextDisplayed){
                axisContainer
                    .selectAll("text")
                    .each(commonFunctions.truncateSVGText((maxAxisLabelLengthPx ? maxAxisLabelLength : optimumLabelLengthPx)));
            }
        } else {
            axisContainer
                .call(axisSettings.axis.orient(orientation));
        }

        if (isTextDisplayed){
            //angle the bottom axis to save space
            if (orientation === "bottom"){
                axisContainer
                    .selectAll("text")
                    .style("text-anchor", "end")
                    .attr("dx", "-0.8em")
                    .attr("dy", "0.15em")
                    .attr("transform", "rotate(-45)");
            }

            commonFunctions.addDelegateEvent(axisContainer, "mouseover", "text.truncated", function(d) {
                axisLabelTip.attr('class', 'd3-tip animate');
                axisLabelTip.show(d);
            });

            commonFunctions.addDelegateEvent(axisContainer, "mouseout", "text.truncated", function(d) {
                axisLabelTip.attr('class', 'd3-tip');
                axisLabelTip.hide();
            });
        }
    };

    commonFunctions.truncateSVGText = function(width) {
        return commonFunctions.truncateText(width, function(node) {
            return node.getComputedTextLength();
        });
    };

    commonFunctions.zoomed = function(svg) {
        return function() {
            svg.attr("transform","translate(" + d3.event.translate + ") scale(" + d3.event.scale + ")");
        };
    };

    commonFunctions.isTrue = function(val, defaultIfNotSet) {
        if (arguments.length === 2 && typeof(val) === "undefined") {
            return defaultIfNotSet;
        } else {
            return typeof(val) !== "undefined" && (val === true || (typeof(val) === "string" && val.toLowerCase() === "true"));
        }
    };


    //Object that describes the visibility state of a legend key
    commonFunctions.legendState = function(key, isVisible) {
        this.key = key;
        this.isVisible = isVisible;

        this.toggleState = function() {
            this.isVisible = !this.isVisible;
        };
    };

    //Wrapper for the map that holds the visibility states of all the legend keys
    //with various accessor and helper methods
    commonFunctions.legendStateMap = function() {
        //The map that holds instances of legenedState keyed by the legend key
        var stateMap = {};

        this.get = function(key) {
            return stateMap[key];
        };

        this.put = function(key, value) {
            stateMap[key] = value;
        };

        this.putIfAbsent = function(key, value) {
            if (!stateMap.hasOwnProperty(key)) {
                stateMap[key] = value;
            }
        };

        //return the number of keys in the legend state map with the passed
        //visibiity state, or an unfiltered count if no args are passed
        this.getCountByState = function(isVisible) {
            var keys = d3.keys(stateMap);
            if (arguments.length === 0) {
                //return the unfiltered 
                return keys.length;
            } else {
                return keys.filter(function(k) {
                    return stateMap[k].isVisible === isVisible;
                }).length;
            }
        };

        this.isVisible = function(key) {
            if (stateMap.hasOwnProperty(key)){
                return stateMap[key].isVisible;
            } else {
                //if not in the map then assume visible
                return true;
            }
        };
    };


    visualisations.commonFunctions = commonFunctions;
    visualisations.commonConstants = commonConstants;
}();
