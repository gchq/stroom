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
visualisations.Doughnut= function(containerNode) {

    var commonFunctions = visualisations.commonFunctions;
    var commonConstants = visualisations.commonConstants;

    var initialised = false;

    var visSettings;
    var visContext;

    if (containerNode){
        var element = containerNode;
    } else {
        var element = window.document.createElement("div");
    }
    this.element = element;

    var grid = new visualisations.GenericGrid(element);

    var tip;
    var inverseHighlight;

    var d3 = window.d3;
    var margins = commonConstants.margins();

    // Create a colour set.
    var colour = commonConstants.categoryGoogle();
    var nameFieldPosition = 0;
    var pieFilterPercentThreshold = 0.2;
    var width;
    var height;

    var r;
    var i;
    var textOffset = 15;
    var tweenDuration = 2000;
    var labelVisibilityPercentageThreshold = 5;
    var canvas;
    var svg;
    var arc_group;
    var label_group;
    var center_group;
    var paths;
    var whiteCircle;
    var totalLabel;
    var totalValue;
    var arc;

    // OBJECTS TO BE POPULATED WITH DATA LATER
    var lines, labels;
    var total = 0;
    var oldTotal = 0;
    var pieData = [];
    var oldPieData = [];
    var filteredPieData = [];
    var showLabels = false;

    //one off initialisation of all the local variables, including
    //appending various static dom elements
    var initialise = function() {
        initialised = true;

        width = commonFunctions.gridAwareWidthFunc(true, containerNode, element);
        height = commonFunctions.gridAwareHeightFunc(true, containerNode, element);

        r = Math.max((Math.min(width - 120, height - 60) / 2), 0);
        ir = r / 2;


        if (typeof(tip) == "undefined") {
            inverseHighlight = commonFunctions.inverseHighlight();
            tip = inverseHighlight.tip()
                .html(function(tipData) { 
                    var html = inverseHighlight.htmlBuilder()
                        .addTipEntry("Name",commonFunctions.autoFormat(tipData.values.name, visSettings.nameDateFormat))
                        .addTipEntry("Value",commonFunctions.autoFormat(tipData.values.value))
                        .addTipEntry("Value %",commonFunctions.autoFormat(tipData.values.value / total * 100) + "%")
                        .build();

                    return html;
                });
        }

        // =========================================================
        // CREATE VIS & GROUPS
        // =========================================================

        canvas = d3.select(element).append("svg:svg");
        svg = canvas.append("svg:g");
        svg.attr("transform", "translate(" + margins.left + "," + margins.top + ")");

        // GROUP FOR ARCS/PATHS
        arc_group = svg.append("svg:g").attr("class", "arc");

        // GROUP FOR LABELS
        label_group = svg.append("svg:g").attr("class", "label_group");

        // GROUP FOR CENTER TEXT
        center_group = svg.append("svg:g").attr("class", "center_group");


        // PLACEHOLDER GRAY CIRCLE
        paths = arc_group.append("svg:circle").attr("fill", "#EFEFEF");

        // =========================================================
        // CENTER TEXT
        // =========================================================

        // WHITE CIRCLE BEHIND LABELS
        whiteCircle = center_group.append("svg:circle").attr("fill", "white");

        // "TOTAL" LABEL
        totalLabel = center_group.append("svg:text").attr("class", "label");
        totalLabel.attr("dy", -7)
            .attr("text-anchor", "middle") // text-align: right
            .attr("fill", "#444")
            .style("font-weight", "bold")
            .text("TOTAL");

        // TOTAL TRAFFIC VALUE
        totalValue = center_group.append("svg:text").attr("class", "total");
        totalValue.attr("dy", 7)
            .attr("text-anchor", "middle")
            .attr("fill", "#444")
            .style("font-weight", "bold");

        arc_group.call(tip);

        // D3 helper function to draw arcs, populates parameter "d" in path object
        arc = d3.svg.arc().startAngle(function(d) {
            return d.startAngle;
        }).endAngle(function(d) {
            return d.endAngle;
        }).innerRadius(ir).outerRadius(r);
    }
    // =========================================================
    // MOUSE HIGHLIGHTING SUPPORT
    // =========================================================

    var highlight = null;

    //Method to allow the grid to call back in to get new instances for each cell
    this.getInstance = function(containerNode) {
        return new visualisations.Doughnut(containerNode);
    }

    //Public method for setting the data on the visualisation(s) as a whole
    //This is the entry point from Stroom
    this.setData = function(context, settings, data) {

        if (data && data !==null) {
            // If the context already has a colour set then use it, otherwise set it
            // to use this one.
            if (context) {
                if (context.color) {
                    colour = context.color;
                } 
            }

            //#########################################################
            //Perform any visualisation specific data manipulation here
            //#########################################################

            //This vis may process ordinal fields so determine all the unique values
            //commonFunctions.computeUniqueValues(data, function(type, fieldPosition) {
            //return fieldPosition == nameFieldPosition ? true : false;
            //});

            if (settings) {

                //if (settings.gridSeries != undefined && settings.gridSeries != ''){
                //if (grid == undefined){
                ////initialise the grid on first call
                //grid = new visualisations.GenericGrid(element);
                //}

                //Inspect settings to determine which axes to synch, if any.
                //Change the settings property(s) used according to the vis
                var synchedFields = [];

                if (settings.synchNames && settings.synchNames.toLowerCase() == "true") {
                    synchedFields.push(0);
                    //values are synched so colour domain uses values from all grid cells
                    //so value X in one cell is the same colour as value X in another
                    //commonFunctions.setColourDomain(colour, data, nameFieldPosition, "VALUE");
                    context.color = colour;
                } else {
                    //ensure there is no colour scale in the context so each grid cel vis can define its own
                    delete context.color;
                }

                //Get grid to construct the grid cells and for each one call back into a 
                //new instance of this to build the visualisation in the cell
                //The last array arg allows you to synchronise the scales of fields
                grid.buildGrid(context, settings, data, this, commonConstants.transitionDuration, synchedFields);
                //} else {
                ////ensure there is no colour scale in the context so each grid cel vis can define its own
                //delete context.color;

                ////not using a grid so just use this instance of the vis to build it inside element
                //this.setDataInsideGrid(context, settings, commonFunctions.unNestData(data));
                //}
            }
        }
    };

    // =========================================================
    // SET DATA
    // =========================================================

    this.setDataInsideGrid = function(context, settings, data) {

        if (!initialised){
            initialise();
        }

        showLabels = commonFunctions.isTrue(settings.showLabels);

        //call to hide as it is possible to get quirks where a tip is shown during a zoom transition and there is then no way for
        //it to disappear
        tip.hide();

        //re-compute all the unique values
        commonFunctions.computeUniqueValues(data, function(type, fieldIndex) {
            return fieldIndex == nameFieldPosition ? true : false;
        });
        //re-compute all the unique keys
        commonFunctions.computeUniqueKeys(data);

        // If the context already has a colour set then use it
        // otherwise define a colour scale based on the pie slice names
        if (context) {
            visContext = context;
            if (context.color) {
                colour = context.color;
            } else {
                commonFunctions.setColourDomain(colour, data, nameFieldPosition, null);
            }
        }

        if (settings){
            visSettings = settings;
        }

        // D3 helper function to populate pie slice parameters from array
        // data
        var donut = d3.layout.pie()
            .value(function(d) {
                return d[1];
            });
        if (!settings.sort || settings.sort == "false") {
            donut.sort(null);
        }


        var filterData = function(element, index, array) {
            element.name = dat[index][0];
            element.value = dat[index][1];
            total += element.value;
            var preFilterPercentage = element.value / preFilterTotal * 100;
            //if (preFilterPercentage <= pieFilterPercentThreshold) {
                //console.log("preFilterTotal: " + preFilterTotal + 
                    //" element.value: " + element.value + 
                    //" preFilterPercentage: " + preFilterPercentage);
            //}
            //return (element.value > -10000);
            return (preFilterPercentage > pieFilterPercentThreshold);
        };

        // Get 1st and only series.
        oldTotal = total;
        total = 0;
        var dat = data.visibleValues();
        var preFilterTotal = d3.sum(dat, function(d) { return d[1];});
        pieData = donut(dat);
        oldPieData = filteredPieData;
        filteredPieData = pieData.filter(filterData);

        var fillFunc = function(d, i) {
            return colour(d.name);
        };
        //var colourValFunc = function(d, i) {
        //return commonFunctions.removeColourHash(colour(d.name));
        //};

        if (filteredPieData.length > 0) {
            // REMOVE PLACEHOLDER CIRCLE
            arc_group.selectAll("circle").remove();

            totalValue.transition()
                .duration(tweenDuration)
                .tween("text",totalTween);

            // DRAW ARC PATHS
            paths = arc_group.selectAll("path")
                .data(filteredPieData, function(d) {
                    return d.name;
                });

            //new data
            paths.enter()
                .append("svg:path")
                .attr("stroke", "white")
                .attr( "stroke-width", 1)
                .transition()
                .duration(tweenDuration)
                .attrTween("d", pieTween);

            //new or updated data
            paths
                .attr("class", commonFunctions.makeColouredElementClassStringFunc(function(d) { 
                    return d.name; 
                }))
                .attr("fill", fillFunc)
                .transition()
                .duration(tweenDuration)
                .attrTween("d", pieTween);

            //removed data
            paths.exit()
                .transition()
                .duration(tweenDuration)
                .attrTween("d", removePieTween)
                .remove();

            if (showLabels) {
                // DRAW TICK MARK LINES FOR LABELS
                lines = label_group.selectAll("line").data(filteredPieData,
                    function(d) {
                        return d.name;
                    });
                    lines.enter().append("svg:line").attr("x1", 0).attr("x2", 0).attr(
                        "stroke", "black").attr("opacity", "0.3");
                        lines.transition()
                            .duration(tweenDuration)
                            .attrTween("transform", linesTween)
                            .style("opacity", function(d) {
                                var percentage = (d.value / total) * 100;
                                return percentage < labelVisibilityPercentageThreshold ? 0 : 1;
                            });


                        lines.exit().transition()
                            .duration(tweenDuration)
                            .attrTween("transform", removeLinesTween)
                            .attrTween("opacity", opacityTween)
                            .remove();

                        // DRAW LABELS WITH PERCENTAGE VALUES
                        labels = label_group.selectAll(".label")
                            .data(filteredPieData, function(d) {
                                return d.name;
                            });
                        var label = labels.enter()
                            .append("svg:g")
                            .attr("class", "label");

                        label.append("svg:text")
                            .attr("class", "name")
                            .attr("fill", "#444")
                            .style("cursor", "default")
                            .style("font-weight", "bold");

                        label.append("svg:text")
                            .attr("class", "value")
                            .attr("fill", "#444")
                            .style("cursor", "default")
                            .style("font-weight", "bold")
                            .attr("dy", "12");


                        labels.exit()
                            .transition()
                            .duration(tweenDuration)
                            .attrTween("transform", removeLabelTween)
                            .attrTween("opacity", opacityTween)
                            .remove();

                        labels.each(function(d) {
                            var e = d3.select(this);
                            var percentage = (d.value / total) * 100;

                            e.style("opacity", function(d) {
                                return percentage < labelVisibilityPercentageThreshold ? 0 : 1;
                            });

                            e.select(".name")
                                .text(function(d) {
                                    return commonFunctions.autoFormat(d.name, visSettings.nameDateFormat);
                                    //return d.name;
                                });
                            e.select(".value")
                                .transition()
                                .duration(tweenDuration)
                                .tween("text", textTween);
                        });

                        labels.transition()
                            .duration(tweenDuration)
                            .attrTween("transform", labelTween);
            }

            update(tweenDuration);
        }
    };

    // =========================================================
    // FUNCTIONS
    // =========================================================

    var pieTween = function(d, i) {
        if (d) {
            var startAngles = [ 0, d.startAngle ];
            var endAngles = [ 0, d.endAngle ];

            // See if we have seen this segment before.
            var oldSegment = getOldSegment(d);
            if (oldSegment) {
                startAngles[0] = oldSegment.startAngle;
                endAngles[0] = oldSegment.endAngle;
            }

            // Calculate the mid point angles.
            var midAngles = [ (startAngles[0] + endAngles[0]) / 2,
                (startAngles[1] + endAngles[1]) / 2 ];
            // See if the transition should go over the top. If it should add 2
            // PI radians.
            if (midAngles[0] - midAngles[1] > Math.PI) {
                startAngles[1] += Math.PI * 2;
                endAngles[1] += Math.PI * 2;
            }

            var interpolate = d3.interpolate({
                startAngle : startAngles[0],
                endAngle : endAngles[0]
            }, {
                startAngle : startAngles[1],
                endAngle : endAngles[1]
            });
            return function(t) {
                var b = interpolate(t);
                return arc(b);
            };
        }
    };

    var removePieTween = function(d, i) {
        var startAngles = [ d.startAngle, 2 * Math.PI ];
        var endAngles = [ d.endAngle, 2 * Math.PI ];

        var interpolate = d3.interpolate({
            startAngle : startAngles[0],
            endAngle : endAngles[0]
        }, {
            startAngle : startAngles[1],
            endAngle : endAngles[1]
        });
        return function(t) {
            var b = interpolate(t);
            return arc(b);
        };
    };

    var labelTween = function(d, i) {
        if (d) {
            var e = d3.select(this);
            var oldSegment = getOldSegment(d);

            var midAngles = [ 0, (d.startAngle + d.endAngle) / 2 ];
            if (oldSegment) {
                midAngles[0] = (oldSegment.startAngle + oldSegment.endAngle) / 2;
            }
            // See if the transition should go over the top. If it should add 2
            // PI radians.
            if (midAngles[0] - midAngles[1] > Math.PI) {
                midAngles[1] += 2 * Math.PI;
            }

            var fn = d3.interpolateNumber(midAngles[0], midAngles[1]);
            return function(t) {
                var val = fn(t);
                var box = e.node().getBBox();
                return "translate("
                    + (((Math.sin(val) * (r + textOffset + (box.width / 2)))) - (box.width / 2))
                    + "," + (-((Math.cos(val) * (r + textOffset + (box.height / 2)))))
                    + ")";
            };
        }
    };

    var removeLabelTween = function(d, i) {
        if (d) {
            var e = d3.select(this);
            var midAngles = [ (d.startAngle + d.endAngle) / 2, 2 * Math.PI ];
            var fn = d3.interpolateNumber(midAngles[0], midAngles[1]);
            return function(t) {
                var val = fn(t);
                var box = e.node().getBBox();
                return "translate("
                    + (((Math.sin(val) * (r + textOffset + (box.width / 2)))) - (box.width / 2))
                    + "," + (-((Math.cos(val) * (r + textOffset + (box.height / 2)))))
                    + ")";
            };
        }
    };

    var textTween = function(d, i) {
        if (d) {
            var oldSegment = getOldSegment(d);
            var oldValue = 0;
            var oldPercent = 0;
            if (oldSegment) {
                oldValue = oldSegment.value;
                oldPercent = (oldValue / oldTotal) * 100;
            }

            var fnPercent = d3.interpolateNumber(oldPercent, (d.value / total) * 100);
            var fnValue = d3.interpolateNumber(oldValue, d.value);
            return function(t) {
                var percent = fnPercent(t);
                var value = fnValue(t);
                this.textContent = commonFunctions.autoFormat(value) + " (" + percent.toFixed(1) + "%)";
            };
        }
    };

    var totalTween = function(d, i) {
        var fn = d3.interpolateNumber(oldTotal, total);
        return function(t) {
            var val = fn(t);
            this.textContent = commonFunctions.autoFormat(val);
        };
    };

    var linesTween = function(d, i) {
        if (d) {
            var oldSegment = getOldSegment(d);

            var midAngles = [ 0, (d.startAngle + d.endAngle) / 2 ];
            if (oldSegment) {
                midAngles[0] = (oldSegment.startAngle + oldSegment.endAngle) / 2;
            }
            // See if the transition should go over the top. If it should add 2
            // PI radians.
            if (midAngles[0] - midAngles[1] > Math.PI) {
                midAngles[1] += Math.PI * 2;
            }

            var fn = d3.interpolateNumber(midAngles[0], midAngles[1]);
            return function(t) {
                var val = fn(t);
                return "rotate(" + val * (180 / Math.PI) + ")";
            };
        }
    };

    var removeLinesTween = function(d, i) {
        if (d) {
            var midAngles = [ (d.startAngle + d.endAngle) / 2, 2 * Math.PI ];
            var fn = d3.interpolateNumber(midAngles[0], midAngles[1]);
            return function(t) {
                var val = fn(t);
                return "rotate(" + val * (180 / Math.PI) + ")";
            };
        }
    };

    var opacityTween = function(d, i) {
        var fn = d3.interpolateNumber(1, 0);
        return function(t) {
            var val = fn(t);
            return "" + val;
        };
    };

    var getOldSegment = function(d) {
        var oldSegment;
        for (var j = 0; j < oldPieData.length; j++) {
            if (oldPieData[j].name === d.name) {
                return oldPieData[j];
            }
        }
        return null;
    };

    var update = function(duration) {
        if (pieData && pieData.length > 0) {
            width = commonFunctions.gridAwareWidthFunc(true, containerNode, element);
            height = commonFunctions.gridAwareHeightFunc(true, containerNode, element);
            var paddingForLabels = showLabels == true ? 60 : 0;

            r = Math.max((Math.min(width - paddingForLabels, height - paddingForLabels) / 2), 0);
            ir = r / 2;

            var halfWidth = width / 2;
            var halfHeight = height / 2;
            var translate = "translate(" + halfWidth + "," + halfHeight + ")";

            canvas
                .attr("width", commonFunctions.gridAwareWidthFunc(false, containerNode, element))
                .attr("height", commonFunctions.gridAwareHeightFunc(false, containerNode, element));

            arc_group.attr("transform", translate);
            label_group.attr("transform", translate);
            center_group.attr("transform", translate);
            paths.attr("r", r);
            whiteCircle.attr("r", ir);

            // Redraw paths with new arc radii.
            arc
                .innerRadius(ir)
                .outerRadius(r);
            paths
                .transition()
                .duration(duration)
                .attrTween("d", pieTween);

            if (showLabels) {
                // Change positions of tick mark lines.
                label_group.selectAll("line").attr("y1", -r - 3).attr("y2", -r - 8);
                // Change positions of value labels.
                label_group
                    .selectAll(".label")
                    .attr(
                        "transform",
                        function(d) {
                            var val = (d.startAngle + d.endAngle) / 2;
                            var e = d3.select(this);
                            var box = e.node().getBBox();
                            return "translate("
                                + (((Math.sin(val) * (r + textOffset + (box.width / 2)))) - (box.width / 2))
                                + ","
                                + (-((Math.cos(val) * (r + textOffset + (box.height / 2)))))
                                + ")";
                        });
            }

            commonFunctions.addDelegateEvent(svg, "mouseover", "path", inverseHighlight.makeInverseHighlightMouseOverHandler(null, pieData.types, svg, "path"));
            commonFunctions.addDelegateEvent(svg, "mouseout", "path", inverseHighlight.makeInverseHighlightMouseOutHandler(svg, "path"));
        }
    };

    this.resize = function() {
        commonFunctions.resize(grid, update, element, margins, width, height);
    };

    this.teardown = function() {
        if (typeof(tip) != "undefined"){
            tip.destroy();
        }
    };

    this.getColourScale = function(){
        return colour;
    };

    //The position in the vaules array that will be used for the legend key
    this.getLegendKeyField = function() {
        return nameFieldPosition;
    };

};
