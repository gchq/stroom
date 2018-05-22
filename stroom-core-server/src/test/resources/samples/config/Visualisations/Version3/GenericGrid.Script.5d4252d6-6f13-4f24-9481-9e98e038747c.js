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
 * Generic script to enable multiple visualisations of the same type to be displayed in a grid
 *
 * REQUIRES:
 * 		d3
 * 		d3-grid
 * 		font-awesome
 */

//TODO - Could do with a big refactor of the individual visualisations to get a separation between the object 
//that Stroom calls and the object instances created for each grid cell.  At the moment it is just a single object
//i.e. visualisations.BarChart which contains everything.  We could instead have visualisations.BarChart and
//visualisations.BarChartContent, with the former exposing setData() and the latter exposing setDataInsideGrid().
//Further to this we could do with some form of prototype for a geeric gridded visualisation as we have a lot of 
//repeated code.

if (!visualisations) {
    var visualisations = {};
}
visualisations.GenericGrid = function(element) {

    this.element = element;

    var commonFunctions = visualisations.commonFunctions;
    var commonConstants = visualisations.commonConstants;

    //padding in px outside each cell visible border box
    var interCellPadding = 5;
    //padding in px inside each cell visible border box
    var intraCellPadding = 5;

    var margin = {
        top: interCellPadding,
        right: interCellPadding,
        bottom: interCellPadding,
        left: interCellPadding
    };

    var gridLayoutPadding = [0.05, 0.05];
    var gridLayoutPadding = [0, 0];
    var chartHeaderTextHeight = 18;
    var legendIconWidth = 15;
    var zoomIconWidth = 15;
    var iconPadding = 5;

    var d3 = window.d3;

    var colour = commonConstants.categoryGoogle();

    var seriesLabelTip;
    var legendKeyField;

    var paddedCanvasWidthFunc = function() {
        return element.clientWidth - margin.left - margin.right;
    };

    var paddedCanvasHeightFunc = function() {
        return element.clientHeight - margin.top - margin.bottom;
    };

    var chartsAreaWidthFunc = function() {
        //in case we want something outside the charts grid
        return paddedCanvasWidthFunc();
    };

    var chartsAreaHeightFunc = function() {
        //in case we want something outside the charts grid
        return paddedCanvasHeightFunc();
    };

    var setCanvasDimensions = function (canvasNode) {
        canvasNode
            .attr("x",   "0px")
            .attr("y",    "0px")
            .attr("width", element.clientWidth  + "px")
            .attr("height", element.clientHeight + "px");
    };

    var setPaddedCanvasDimensions = function (paddedCanvasNode) {
        paddedCanvasNode
            .attr("x",   margin.left + "px")
            .attr("y",    margin.top + "px")
            .attr("width",  paddedCanvasWidthFunc() + "px")
            .attr("height", paddedCanvasHeightFunc() + "px");
    };

    var setChartsAreaCanvasDimensions = function (chartsAreaCanvasNode) {
        chartsAreaCanvasNode
            .attr("x", "0px")
            .attr("y", "0px")
            .attr("width", chartsAreaWidthFunc() + "px")
            .attr("height", chartsAreaHeightFunc() + "px");
    };

    var width = chartsAreaWidthFunc();
    var height = chartsAreaHeightFunc();

    var legendsContainer = d3
        .select(element)
        .append("div")
        .attr("class", "vis-legends");

    var canvas = d3
        .select(element)
        .append("svg:svg")
        .attr("class", "vis-canvas");

    var paddedCanvas = canvas
        .append("svg:svg")
        .attr("class", "vis-paddedCanvas");

    var chartsAreaCanvas = paddedCanvas
        .append("svg:svg")
        .attr("class", "vis-chartsAreaCanvas");

    //Add the series data.
    var seriesContainer = chartsAreaCanvas
        .append("svg:g")
        .attr("class", "vis-seriesGroup");

    var chartsGridLayout = d3.layout.grid()
        .bands()
        .size([width,height])
        .rows(1)
        .padding(gridLayoutPadding);

    var getCellWidth = function(){
        return chartsGridLayout.nodeSize()[0];
    };

    var getCellHeight = function(){
        return chartsGridLayout.nodeSize()[1];
    };
    var getUsableChartHeight = function(){
        return getCellHeight() - (interCellPadding * 2) - (intraCellPadding * 2) - chartHeaderTextHeight;
    };

    var getGridRows = function(){
        if (chartsGridLayout.size()[1] == 0){
            return 0;
        } else {
            return Math.round(chartsGridLayout.size()[1] / chartsGridLayout.nodeSize()[1]);
        }
    };

    var getGridCols = function(){
        if (chartsGridLayout.size()[0] == 0){
            return 0;
        } else {
            return Math.round(chartsGridLayout.size()[0] / chartsGridLayout.nodeSize()[0]);
        }
    };

    var transitionDuration = 750;
    var isZoomedIn = false;
    var visData;
    var enabledData;
    var visualisationMap = {};
    var legendMap = {};
    var legendStateMap = new commonFunctions.legendStateMap();
    var callingVis;
    var visContext;
    var visSettings;
    var visSynchedFields;

    var setRectDimensions = function () {
        this.attr("x",   function(d) {return d.x +"px";})
            .attr("y",    function(d) {return d.y +"px";})
            .attr("width",  getCellWidth() + "px")
            .attr("height", getCellHeight() + "px");
    };

    var removeInvisibleSeries = function(gridCellData) {
        if (gridCellData.values){
            var visibleSeriesCount = 0;
            gridCellData.values.forEach(function(singleSeriesData) {
                if (singleSeriesData.key) {
                    singleSeriesData.isVisible = legendStateMap.isVisible(singleSeriesData.key);
                    if (singleSeriesData.isVisible === true) {
                        visibleSeriesCount++;
                    }
                }
            });
            gridCellData.isVisible = (visibleSeriesCount !== 0);
            return gridCellData;
        };
    };

    var removeInvisibleDataPoints = function(keyFieldIndex) {
        var prunePoints = function(data) {
            if (data.values && data.values.constructor === Array && data.values.length > 0){
                var visibleSeriesCount = 0;
                data.values.forEach(function(point) {
                    point.isVisible = legendStateMap.isVisible(point[keyFieldIndex]);
                    if (point.isVisible === true) {
                        visibleSeriesCount++;
                    }
                });
                data.isVisible = (visibleSeriesCount !== 0);
            }
        };

        //return a function that will do the 'removal' for the required keyFieldIndex
        return function(data) {
            if (data.values && data.values.constructor === Array) {
                if (data.values.length > 0) {
                    if (data.values[0].hasOwnProperty("key")){
                        data.values.forEach(function(d) {
                            prunePoints(d);
                        });
                    } else {
                        prunePoints(data);
                    }
                }
            }
            return data;
        };
    };

    var removeInvisibleData = function(data, removalFunc) {
        if (data.values && data.values.constructor === Array) {
            data.values.forEach(function(gridCellData) {
                removalFunc(gridCellData);
            });
        }
    };

    //closure to return a handler with the correct data attached
    var makeSeriesZoomMouseHandler = function(d) {

        var d = d;
        var seriesZoomMouseHandler = function() {
            //console.log('zoomhandler called series: ' + d.key + ' valueCount:' + d.values.length);
            if (isZoomedIn) {
                //Zooming OUT so mark all as visible
                visData.values.forEach(function(val) {
                    val.isZoomVisible = true;
                });
                isZoomedIn = false;
                update(transitionDuration);
            } else {
                //Zooming IN so mark only the zoomed in cell as visible
                visData.values.forEach(function(val) {
                    val.isZoomVisible = (val.key === d.key);
                });
                isZoomedIn = true;
                update(transitionDuration);
            }
        }
        return seriesZoomMouseHandler;
    };

    var toggleLegend = function(visNode, legendNode, colourScale, setDataFunc){
        var legendDiv = d3.select(legendNode).node();
        if (typeof(legendDiv) != "undefined" ) {
            if (!legendDiv.html()) {
                var rightPos = document.documentElement.clientWidth - (visNode.x.baseVal.value + visNode.width.baseVal.value) + interCellPadding;
                var topPos = margin.top + visNode.y.baseVal.value + interCellPadding + intraCellPadding + chartHeaderTextHeight;

                //position the legend box
                legendDiv
                    .style("position", "absolute")
                    .style("top", topPos + "px")
                    .style("right", rightPos + "px");

                if (legendKeyField === null) {
                    //legend based on series
                    var legendKeyFormatFunc = formatSeriesKey;
                } else {
                    var legendKeyFormatFunc = formatName;
                }

                //TODO currently hard coded to always add the mouse events but need to make this
                //conditional on the coloured elements (e.g. series) being synched between grid cells
                commonFunctions.buildLegend(
                    legendDiv,
                    colourScale,
                    true,
                    visNode.width.baseVal.value,
                    visNode.height.baseVal.value,
                    legendStateMap,
                    setDataFunc,
                    legendKeyFormatFunc);
            } else {
                legendDiv.html("");
            }
        }
    };

    var makeLegendMouseHandler = function(visNode, data) {
        //console.log('makeLegendMouseHandler called for key: ' + data.key);
        var cellVis = visualisationMap[data.key];
        var legendNode = legendMap[data.key];
        var colourScale;
        if (cellVis.hasOwnProperty("getColourScale")) {
            colourScale = cellVis.getColourScale();
        } else {
            console.log("ERROR - Expecting the visualisation to expose a getColourScale() method");
        }
        //console.log('colourScale range size: ' + colourScale.domain().length);

        //ensure all series keys are in the state map
        colourScale.domain().forEach(function(domainValue) {
            legendStateMap.putIfAbsent(domainValue, new commonFunctions.legendState(domainValue, true));
        });

        var legendMouseHandler = function(d) {
            if (typeof(colourScale) != "undefined"){
                //define the function instance to call to refresh the data inside a grid cell
                //so the legend can enable/diable series and then update the vis
                var setDataFunc = function() {
                    update(transitionDuration);
                };
                toggleLegend(visNode, legendNode, colourScale, setDataFunc);
            }
        }
        return legendMouseHandler;
    };

    var clearLegends = function(parentNode){
        parentNode.selectAll(".vis-legend")
            .each(function(d,i) {
                d3.select(this).html("");
            });
    };

    //recurrsive function to add .isVisible and .visibleValues properties
    //to each level of the data tree
    var addVisibilityFeatures = function(d) {
        if (!d.hasOwnProperty("isVisible")){
            d.isVisible = true;
        }
        if (!d.hasOwnProperty("visibleValues")) {
            if (d.values && d.values.constructor === Array) {
                //create a function to expose only the visible values
                d.visibleValues = function() {
                    return d.values.filter(function(d) {
                        //assume true if prop not present
                        return (
                            (!d.hasOwnProperty("isVisible") || d.isVisible) &&
                            (!d.hasOwnProperty("isZoomVisible") || d.isZoomVisible)
                        );
                    });
                };
            } else {
                d.visibleValues = [];
            }
        }
        if (d.values && d.values.constructor === Array) {
            d.values.forEach(function(val) {
                addVisibilityFeatures(val);
            });
        }
    };

    var formatGridKey = function(key) {
        if (visSettings.gridSeriesDateFormat) {
            return commonFunctions.dateToStr(key, visSettings.gridSeriesDateFormat);
        } else {
            return key;
        }
    };

    var formatSeriesKey = function(key) {
        if (visSettings.seriesDateFormat) {
            return commonFunctions.dateToStr(key, visSettings.seriesDateFormat);
        } else {
            return key;
        }
    };

    var formatName = function(name) {
        if (visSettings.nameDateFormat) {
            return commonFunctions.dateToStr(name, visSettings.nameDateFormat);
        } else {
            return name;
        }
    };

    /*
     * context, settings, data - The values passed from Stroom
     * vis - the visualisation object instance, e.g. an instance of Doughnut
     * duration - the d3 transition duration
     * synchedFields - an array of field positions in the data structure that you want to
     * 		synchronise the scales for, e.g. pass [0,2] to synch fields 0 and 2.
     */
    this.buildGrid = function(context, settings, data, vis, duration, synchedFields) {

        callingVis = vis;
        visSettings = settings;
        visContext = context;
        visSynchedFields = synchedFields;

        //Ensure all branches have key properties as a lot of the visuaisations rely on them
        commonFunctions.cleanMissingKeys(data);

        if (callingVis.hasOwnProperty("getLegendKeyField")){
            legendKeyField = callingVis.getLegendKeyField();
        } else {
            legendKeyField = null;
        }

        //set up the chart tile hover tip
        if (typeof(seriesLabelTip) == "undefined") {
            seriesLabelTip = d3.tip()
                .direction(commonFunctions.d3TipDirectionFunc)
                .attr('class', 'd3-tip')
                .html(commonFunctions.makeD3TipBasicTextHtmlFunc(function(d) { 
                    return d.key;
                }));
        }

        //Add the visibility properties and functions to the data tree
        addVisibilityFeatures(data);

        visData = data;

        //Build the grid and populate each cell with a vis
        update(duration);
    }


    var update = function(duration) {
        //clear any visible legends
        clearLegends(legendsContainer);

        //clear the contents of any d3-tip nodes
        d3.selectAll(".d3-tip")
            .each(function(d, i) {
                d3.select(this).html("");
            });

        if (visData) {
            var legendKeyField;
            if (!callingVis.hasOwnProperty("getLegendKeyField") || callingVis.getLegendKeyField() === null) {
                //vis uses the grouping keys for its legend
                var removalFunc = removeInvisibleSeries;
                legendKeyField = null;
            } else {
                legendKeyField = callingVis.getLegendKeyField();
                var removalFunc = removeInvisibleDataPoints(legendKeyField);
            }
            removeInvisibleData(visData, removalFunc);
            
            //now re-compute the aggregates as we may have 'removed' data
            commonFunctions.dataAggregator()
                .setRecursive(true)
                .setUseVisibleValues(true)
                .aggregate(visData);

            //get the unique values for the designated key field over all grid cells
            commonFunctions.computeUniqueValues(visData, function(type, index) {
                return (index === legendKeyField || visData.types[index] === "TEXT" || visData.types[index] === "GENERAL");
            });
                
            //find all the unique series keys 
            commonFunctions.computeUniqueKeys(visData);

            if (commonFunctions.isTrue(visSettings.synchSeries)) {
                //create a single coulour scale for all grid cells as they are synched
                commonFunctions.setColourDomain(colour, visData, legendKeyField, "SYNCHED_SERIES");
                visContext.color = colour;
            } else if (commonFunctions.isTrue(visSettings.synchNames)) {
                //create a single coulour scale for all grid cells as they are synched
                commonFunctions.setColourDomain(colour, visData, legendKeyField, "VALUE");
                visContext.color = colour;
            }

            //clone the types and uniqueValues arrays down to each series sub-element
            //and synch the aggregates of all specified fields
            if (!isZoomedIn) {
                visData.values.forEach(function(gridCellData, i, wholeArray) {
                    gridCellData.types = visData.types;
                    if (visSynchedFields){
                        visSynchedFields.forEach(function(arrPosToSync) {
                            gridCellData.min[arrPosToSync] = visData.min[arrPosToSync];
                            gridCellData.max[arrPosToSync] = visData.max[arrPosToSync];
                            gridCellData.sum[arrPosToSync] = visData.sum[arrPosToSync];
                            if (visData.unique) {
                                gridCellData.unique[arrPosToSync] = visData.unique[arrPosToSync] ;
                            }
                            if (visData.visibleUnique) {
                                gridCellData.visibleUnique[arrPosToSync] = visData.visibleUnique[arrPosToSync] ;
                            }
                        });
                    }
                });
            } else {
                //zoomed in so re-compute the aggregates for the single grid cell so the axes are scaled to its data
                //Only need to re-compute aggregates one level deep as the rest should be uneffected
                if (visData.visibleValues){
                    var zoomedInCellData = visData.visibleValues()[0];
                    if (zoomedInCellData){
                        commonFunctions.dataAggregator()
                            .setRecursive(false)
                            .setUseVisibleValues(true)
                            .aggregate(zoomedInCellData);
                    }
                }
            }

            var visibleValues = visData.visibleValues();
            var duration = typeof(defaultDuration) != "undefined" ? defaultDuration : duration;
            var requiresZoomInOutCapability = (isZoomedIn || visibleValues.length > 1);
            var requiresLegend = commonFunctions.isTrue(visSettings.requiresLegend, true);

            width = chartsAreaWidthFunc();
            height = chartsAreaHeightFunc();

            //update all the canvas dimensions based on the current window area
            setCanvasDimensions(canvas);
            setPaddedCanvasDimensions(paddedCanvas);
            setChartsAreaCanvasDimensions(chartsAreaCanvas);

            seriesContainer.call(seriesLabelTip);

            commonFunctions.addDelegateEvent(seriesContainer, "mouseover", ".vis-cellVisualisation-seriesName.truncated", function(d) {
                seriesLabelTip.attr('class', 'd3-tip animate');
                seriesLabelTip.show(d);
            });
            commonFunctions.addDelegateEvent(seriesContainer, "mouseout", ".vis-cellVisualisation-seriesName.truncated", function(d) {
                seriesLabelTip.attr('class', 'd3-tip');
                seriesLabelTip.hide();
            });

            //D3 data binding for legends
            var legends = legendsContainer.selectAll(".vis-legend")
                .data(visibleValues, function(d) { 
                    //console.log('key function key: ' + d.key);
                    return d.key;
                });

            legends.enter()
                .append("div")
                .attr("class","vis-legend")
                .each(function(d,i) {
                    legendMap[d.key] = d3.select(this);
                });

            legends.exit()
                .each(function(d,i) {
                    delete legendMap[d.key];
                })
                .remove();

            //Construct the grid layout 
            chartsGridLayout = d3.layout.grid()
                .bands()
                .size([width,height])
                .padding(gridLayoutPadding);

            //D3 data binding for the grid cells
            var gridCells = seriesContainer.selectAll(".vis-cellVisualisation")
                .data(chartsGridLayout(visibleValues), function(d) { 
                    //console.log('key function key: ' + d.key);
                    return d.key;
                });

            gridCells.enter()
                .append("svg:svg")
                .attr("class","vis-cellVisualisation")
                .attr("opacity","0")
                .call(function(parentNode) {
                    parentNode.append("svg:rect")
                        .attr("class","vis-cellVisualisation-border")
                        .attr("x", interCellPadding)
                        .attr("y", interCellPadding);
                    parentNode.append("svg:text")
                        .classed("vis-cellVisualisation-seriesName", true)
                        .attr("x", interCellPadding + intraCellPadding);
                    if (visibleValues.length > 1) {
                        parentNode.append("svg:text")
                            .attr("class","vis-cellVisualisation-icon vis-cellVisualisation-zoomIcon");
                    }
                    if (requiresLegend){
                        parentNode.append("svg:text")
                        .attr("class","vis-cellVisualisation-icon vis-cellVisualisation-legendIcon")
                        .text(commonConstants.fontAwesomeLegend);
                    }
                    parentNode.append("svg:svg")
                        .attr("class","vis-cellVisualisation-usableArea")
                        .attr("x", interCellPadding + intraCellPadding);
                })
                .each(addNewSeries)
                .transition()
                .duration(duration);

            gridCells.exit()
                .transition()
                .duration(duration)
                .attr("opacity","0")
                .each(removeExistingSeries)
                .remove();

            gridCells.transition()
                .duration(duration)
                .attr("opacity","1")
                .call(setRectDimensions)
                .each(function(d) {
                    var gridCell = d3.select(this);

                    //work out the length available for the series name
                    var seriesNameSpace = Math.floor(0.95 * (getCellWidth() - (intraCellPadding * 2) - zoomIconWidth - iconPadding - legendIconWidth));

                    gridCell.call(setRectDimensions);

                    gridCell.selectAll(".vis-cellVisualisation-border")
                        .attr("width", getCellWidth() - (interCellPadding * 2))
                        .attr("height",getCellHeight() - (interCellPadding * 2))
                        .style("visibility", function() {
                            return (visibleValues.length > 1) ? "visible" : "hidden";
                        });

                    gridCell.selectAll(".vis-cellVisualisation-seriesName")
                        .attr("y", interCellPadding + intraCellPadding + chartHeaderTextHeight - 2)
                        .classed(function() {
                            var cellWidth = getCellWidth();
                            if (cellWidth < 350) {
                                return {"normal": false, "small": false, "smaller": true};
                            } else if (cellWidth < 450){
                                return{"normal": false, "small": true, "smaller": false};
                            } else {
                                return{"normal": true, "small": false, "smaller": false};
                            }
                        }())
                        .style("cursor", function() {
                            return requiresZoomInOutCapability ? "pointer" : "default";
                        })
                        .text(function(d) {
                            //If there is no grid series then the key will be "Series" so don't display this
                            if (visibleValues.length === 1 && d.key === "Series") {
                                return "";
                            } else {
                                return formatGridKey(d.key);
                            }
                        })
                        .on("mousedown", function() { 
                            if (requiresZoomInOutCapability) {
                                return makeSeriesZoomMouseHandler(d);
                            } else {
                                return null;
                            }
                        }())
                        .each(commonFunctions.truncateSVGText(seriesNameSpace));

                    gridCell.selectAll(".vis-cellVisualisation-zoomIcon")
                        .attr("x", getCellWidth() - (interCellPadding + intraCellPadding + zoomIconWidth))
                        .attr("y", interCellPadding + intraCellPadding + chartHeaderTextHeight - 4)
                        .style("visibility", function() {
                            return (isZoomedIn || visibleValues.length > 1) ? "visible" : "hidden";
                        })
                        .text(commonFunctions.zoomIconTextFunc(isZoomedIn))
                        .on("mousedown", function() { 
                            if (requiresZoomInOutCapability) {
                                return makeSeriesZoomMouseHandler(d);
                            } else {
                                return null;
                            }
                        }());	

                    gridCell.selectAll(".vis-cellVisualisation-usableArea")
                        .attr("y", interCellPadding + (intraCellPadding * 2) + chartHeaderTextHeight)
                        .attr("width", getCellWidth() - (interCellPadding * 2) - (intraCellPadding * 2) )
                        .attr("height", getUsableChartHeight());

                })
                .each(updateExistingSeries)
                .each(function(d) {
                    //creation of the legend handler needs to be done
                    //once the vis has been fed data so its colour scale is
                    //set up
                    var gridCell = d3.select(this);
                    if (requiresLegend) {
                        gridCell.selectAll(".vis-cellVisualisation-legendIcon")
                            .attr("x", getCellWidth() - (interCellPadding + intraCellPadding + zoomIconWidth + iconPadding + legendIconWidth))
                            .attr("y", interCellPadding + intraCellPadding + chartHeaderTextHeight - 4)
                            .on("mousedown",makeLegendMouseHandler(this, d));    
                    }
                });
        }
    }

    var addNewSeries = function(data) {

        //console.log('adding series: ' + data.key + ' valueCount:' + data.values.length);
        var svg = this.getElementsByClassName('vis-cellVisualisation-usableArea')[0];

        //Use the vis object that called us to provide a new instance for the grid cell
        //Each grid cell must have its own visualisations.[VisName] object to keep state separate.
        //The new instance is held in a map keyed on the key so D3 can bind to it
        var cellVis = callingVis.getInstance(svg);
        visualisationMap[data.key] = cellVis;

        //no need to build the vis at this point as we can just build it in the d3 update step
        //otherwise it will be built twice
    }

    var updateExistingSeries = function(data) {
        //console.log('updating series: ' + data.key + ' valueCount:' + data.values.length);
        var cellVis = visualisationMap[data.key];

        var legendIcon = d3.select(this).select(".vis-cellVisualisation-legendIcon");
        if (commonFunctions.isTrue(visSettings.requiresLegend, true)){
            legendIcon
                .style("opacity", "1")
                .style("cursor", "pointer");
        } else {
            legendIcon
                .style("opacity", "0.2")
                .style("cursor", "default");
        }

        //call back to the visualisation to build the vis inside the grid cell
        cellVis.setDataInsideGrid(visContext, visSettings, data);
    }

    var removeExistingSeries = function(data) {
        //console.log('removing series: ' + data.key + ' valueCount:' + data.values.length);

        //Call optional teardown method to do any tidying up before the object is deleted
        var cellVis = visualisationMap[data.key];
        if (cellVis && cellVis.hasOwnProperty("teardown")){
            cellVis.teardown();
        }
        delete visualisationMap[data.key];
    }


    this.resize = function() {
        commonFunctions.resize(null, update, element, margin, width, height);
    };
};
