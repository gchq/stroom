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
if (!visualisations) {
    var visualisations = {};
}

//IIFE to prvide shared scope for sharing state and constants between the controller 
//object and each grid cell object instance
(!function(){

    var d3 = window.d3;
    var commonFunctions = visualisations.commonFunctions;
    var commonConstants = visualisations.commonConstants;
    
    //Constant declarations
    var COLOUR_OUTLIER = visualisations.commonConstants.googleGrey500;
    var COLOUR_GREEN = visualisations.commonConstants.googleGreen500;
    var COLOUR_AMBER = visualisations.commonConstants.googleAmber500;
    var COLOUR_RED = visualisations.commonConstants.googleRed500;
    var STATUS_OUTLIER = "Outlier";
    var STATUS_GREEN = "Green";
    var STATUS_AMBER = "Amber";
    var STATUS_RED = "Red";

    var createRangeText = function(from, to) {
        return commonFunctions.autoFormat(from) + " - " + commonFunctions.autoFormat(to);
    };
    var statusToRangeTextMap = {};

    visualisations.TrafficLights = function() {

        this.element = window.document.createElement("div");
        var grid = new visualisations.GenericGrid(this.element);
        var colour;


        //A reverse scale to map a css colour value back to a status band
        //i.e. COLOUR_GREEN --> STATUS_GREEN
        var reverseLegendColourScale = d3.scale.ordinal()
            .range([
                STATUS_GREEN,
                STATUS_AMBER,
                STATUS_RED,
                STATUS_OUTLIER
            ])
            .domain([
                COLOUR_GREEN,
                COLOUR_AMBER,
                COLOUR_RED,
                COLOUR_OUTLIER
            ]);

        //builds a colour scale based on the thresholds passed in 
        //the vis settings
        var createColourScale = function(settings) {
            var scale = d3.scale.threshold()
                .domain([
                    settings.GreenLo,
                    settings.GreenHi,
                    settings.AmberLo,
                    settings.AmberHi,
                    settings.RedLo,
                    settings.RedHi
                ])
                .range([
                    COLOUR_OUTLIER,
                    COLOUR_GREEN,
                    COLOUR_OUTLIER,
                    COLOUR_AMBER,
                    COLOUR_OUTLIER,
                    COLOUR_RED,
                    COLOUR_OUTLIER
                ]);
            return scale;
        };

        //Method to allow the grid to call back in to get new instances for each cell
        this.getInstance = function(containerNode) {
            return new visualisations.TrafficLights.Visualisation(containerNode);
        };

        //Public method for setting the data on the visualisation(s) as a whole
        //This is the entry point from Stroom
        this.setData = function(context, settings, data) {

            if (data && data !==null) {
                // If the context already has a colour set then use it, otherwise set it
                // to use this one.
                if (context) {
                    if (context.color) {
                        colour = context.color;
                    } else {
                        colour = createColourScale(settings);
                        context.color = colour;
                    }
                }


                //#########################################################
                //Perform any visualisation specific data manipulation here
                //#########################################################
                if (settings){
                    statusToRangeTextMap[STATUS_GREEN] = createRangeText(settings.GreenLo, settings.GreenHi);
                    statusToRangeTextMap[STATUS_AMBER] = createRangeText(settings.AmberLo,settings.AmberHi);
                    statusToRangeTextMap[STATUS_RED] = createRangeText(settings.RedLo,settings.RedHi);
                }

                if (data.values) {
                    data.values.forEach(function(gridCellData) {
                        var colourBand = colour(gridCellData.values[0][0]);
                        var status = reverseLegendColourScale(colourBand);
                        gridCellData.values[0][1] = status;
                        gridCellData.values[0][2] = statusToRangeTextMap[status];
                    });
                }

                if (settings) {
                    data.values = data.values.filter(function(d) {
                        //defualt to true if the setting is not supplied
                        var displayGreens = commonFunctions.isTrue(settings.displayGreens, true);
                        var colourValue = d.values[0][1];
                        //remove and values above/below/between the three status bands and also greens if
                        //the settings say to do that
                        return (colourValue !== STATUS_OUTLIER) && (displayGreens || (!displayGreens && colourValue != STATUS_GREEN)) ;
                    });
                    var synchedFields = [];

                    //Get grid to construct the grid cells and for each one call back into a 
                    //new instance of this to build the visualisation in the cell
                    //The last array arg allows you to synchronise the scales of fields
                    grid.buildGrid(context, settings, data, this, commonConstants.transitionDuration, synchedFields);
                }
            }
        };

        this.resize = function() {
            grid.resize();
        };

        this.getLegendKeyField = function() {
            return 2;
        };

    };

    //This is the content of the visualisation inside the containerNode
    //One instance will be created per grid cell
    visualisations.TrafficLights.Visualisation = function(containerNode) {

        var element = containerNode;
        var margins = commonConstants.margins();
        var width;
        var height;
        var tip;
        var inverseHighlight;

        if (typeof(tip) == "undefined") {
            inverseHighlight = commonFunctions.inverseHighlight();
            tip = inverseHighlight.tip()
                .html(function(tipData) { 
                    var html = inverseHighlight.htmlBuilder()
                        .addTipEntry("Status",tipData.values[1])
                        .addTipEntry("Value",commonFunctions.autoFormat(tipData.values[0]))
                        .build();
                    return html;
                });
        }

        var offColour = "rgb(40,40,40)";

        // Add the series data.
        var seriesContainer ;

        var visData;
        var visSettings;
        var visContext;

        var canvas = d3.select(element)
            .append("svg:svg");
        var svg = canvas.append("svg:g");

        // Add the series data.
        seriesContainer = svg.append("svg:g")
            .attr("class", "vis-series");


        //Public entry point for the Grid to call back in to set the cell level data on the cell level 
        //visualisation instance.
        //data will only contain the branch of the tree for this cell
        this.setDataInsideGrid = function(context, settings, data) {

            // If the context already has a colour set then use it
            if (context) {
                visContext = context;
                if (context.color) {
                    colour = context.color;
                }
            }
            if (settings){
                visSettings = settings;
            }

            visData = data;
            update(0);
        };

        var update = function(duration) {
            if (visData) {
                var visibleValues = visData.visibleValues();

                width = commonFunctions.gridAwareWidthFunc(true, containerNode, element);
                height = commonFunctions.gridAwareHeightFunc(true, containerNode, element);
                fullWidth = commonFunctions.gridAwareWidthFunc(false, containerNode, element);
                fullHeight = commonFunctions.gridAwareHeightFunc(false, containerNode, element);

                canvas
                    .attr("width", fullWidth)
                    .attr("height", fullHeight);

                svg.attr("transform", "translate(" + margins.left + "," + margins.top + ")");
                seriesContainer.call(tip);

                var g = seriesContainer.selectAll(".RAG")
                    .data(visibleValues);

                var ge = g.enter().append("svg:g")
                    .attr("class","RAG");

                ge.append("svg:rect")
                    .attr("rx",2)
                    .attr("ry",2)
                    .attr("width",20)
                    .attr("height",50)
                    .style("fill","black");

                ge.append("svg:circle")
                    .attr("r", "10")
                    .attr("class","red");

                ge.append("svg:circle")
                    .attr("r", "10")
                    .attr("class","amber");

                ge.append("svg:circle")
                    .attr("r", "10")
                    .attr("class","green");

                g.exit()
                    .transition()
                    .duration(duration)
                    .style("opacity",0)
                    .remove();

                g.transition()
                    .duration(duration)
                    .selectAll("circle")
                    .style("opacity",1);

                g.transition()
                    .duration(duration)
                    .selectAll("text");

                g.each(function(d) {
                    var e = d3.select(this);
                    var rect = e.select("rect");
                    var ragStatus = d[1];
                        
                    var circleR = e.select("." + STATUS_RED.toLowerCase())
                        .attr("class", commonFunctions.makeColouredElementClassStringFunc(function(d) {
                            return statusToRangeTextMap[STATUS_RED];
                        }, STATUS_RED.toLowerCase()));

                    var circleA = e.select("." + STATUS_AMBER.toLowerCase())
                        .attr("class", commonFunctions.makeColouredElementClassStringFunc(function(d) {
                            return statusToRangeTextMap[STATUS_AMBER];
                        }, STATUS_AMBER.toLowerCase()));

                    var circleG = e.select("." + STATUS_GREEN.toLowerCase())
                        .attr("class", commonFunctions.makeColouredElementClassStringFunc(function(d) {
                            return statusToRangeTextMap[STATUS_GREEN];
                        }, STATUS_GREEN.toLowerCase()));

                    circleR.style("fill", offColour);
                    circleA.style("fill", offColour);
                    circleG.style("fill", offColour);

                    if (ragStatus == STATUS_RED) {
                        circleR.style("fill", COLOUR_RED);
                    } else if (ragStatus == STATUS_AMBER) {
                        circleA.style("fill", COLOUR_AMBER);
                    } else if (ragStatus == STATUS_GREEN) {
                        circleG.style("fill", COLOUR_GREEN);
                    } 

                    var bbr = rect.node()
                        .getBBox();

                    var widthScale = width / bbr.width;
                    var heightScale = height / bbr.height;
                    var scale = Math.min(heightScale, widthScale);

                    rect.attr("transform", "translate(" + (width - (bbr.width * scale)) / 2 + "," + 1 + ")scale(" + scale + ")");

                    var bbc = circleR.node()
                        .getBBox();

                    circleR.attr("transform", "translate(" + (width) / 2 + "," + bbr.height * scale * 0.1666 + ") scale(" + scale * 0.75 + ")");
                    circleA.attr("transform", "translate(" + (width) / 2 + "," + bbr.height * scale * 0.5 + ") scale(" + scale * 0.75 + ")");
                    circleG.attr("transform", "translate(" + (width) / 2 + "," + bbr.height * scale * (5 / 6) + ") scale(" + scale * 0.75 + ")");


                    //add the hover tip mouse events on the apprpriate circle
                    var cssSelector = "circle." + ragStatus.toLowerCase();
                    commonFunctions.addDelegateEvent(
                        g, 
                        "mouseover", 
                        cssSelector, 
                        inverseHighlight.makeInverseHighlightMouseOverHandler("DUMMY", visData.types, seriesContainer,cssSelector));

                    commonFunctions.addDelegateEvent(
                        g, 
                        "mouseout", 
                        cssSelector, 
                        inverseHighlight.makeInverseHighlightMouseOutHandler(seriesContainer,cssSelector));
                });
            }
        };

        this.teardown = function() {
            if (typeof(tip) != "undefined"){
                tip.destroy();
            }
        };

        this.getColourScale = function(){
            //hard coded colour scale for the legend
            return d3.scale.ordinal()
                .range([
                    COLOUR_RED,
                    COLOUR_AMBER,
                    COLOUR_GREEN
                ])
                .domain([
                    statusToRangeTextMap[STATUS_RED],
                    statusToRangeTextMap[STATUS_AMBER],
                    statusToRangeTextMap[STATUS_GREEN]
                ]);
        };
    };

}());
