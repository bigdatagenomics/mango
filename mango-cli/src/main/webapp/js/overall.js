var readJsonLocation = "/reads/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
var referenceStringLocation = "/reference/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
var varJsonLocation = "/variants/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
var featureJsonLocation = "/features/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;

//Add Region Info
d3.select("h2")
    .text("current region: " + viewRefName + ": "+ viewRegStart + "-" + viewRegEnd);

//Reference
var refContainer = d3.select("#refArea")
    .append("svg")
    .attr("width", width)
    .attr("height", 38);


// Create the scale for the axis
var axisScale = d3.scale.linear()
    .domain([viewRegStart, viewRegEnd])
    .range([0, width]);

// Create the axis
var xAxis = d3.svg.axis()
   .scale(axisScale);

// Add the axis to the container
refContainer.append("g")
    .attr("class", "axis")
    .call(xAxis);

//Adding Reference rectangles
d3.json(referenceStringLocation, function(error, data) {
    refContainer.selectAll("rect").data(data)
    .enter()
        .append("g")
        .append("rect")
            .attr("x", function(d, i) {
                return i/(viewRegEnd-viewRegStart) * width;
            })
            .attr("y", 30)
            .attr("fill", function(d) {
                if (d.reference === "G") {
                    return '#296629'; //DARK GREEN
                } else if (d.reference === "C") {
                    return '#CC2900'; //RED
                } else if (d.reference === "A") { 
                    return '#0066FF'; //BLUE
                } else {
                    return '#FF6600'; //ORANGE
                }
            })
            .attr("width", function(d) {
                return Math.max(1, width/(viewRegEnd-viewRegStart));
            })
            .attr("height", 10)
            .on("mouseover", function(d) {
                div.transition()
                    .duration(200)
                    .style("opacity", .9);
                div.html(d.reference)
                    .style("left", (d3.event.pageX - 10) + "px")
                    .style("top", (d3.event.pageY - 30) + "px");
            })
});

//Features
if (featuresExist === true) {
    var featureSvgContainer = d3.select("#featArea")
        .append("svg")
        .attr("height", 9)
        .attr("width", width);

    d3.json(featureJsonLocation, function(error, data) {
        data.forEach(function(d) {
            d.featureId = d.featureId;
            d.featureType = d.featureType;
            d.start = +d.start;
            d.end = +d.end;
            d.track = +d.track;        
        });

        // Add the rectangles
        featureSvgContainer.selectAll("rect").data(data)
            .enter()
                .append("g")
                .append("rect")
                    .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
                    .attr("y", 5)
                    .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }))
                    .attr("height", (trackHeight-2))
                    .attr("fill", "#6600CC")
                    .on("mouseover", function(d) {
                        div.transition()
                        .duration(200)
                        .style("opacity", .9);
                        div .html(d.featureId)
                        .style("left", (d3.event.pageX) + "px")
                        .style("top", (d3.event.pageY - 28) + "px");
                    })
                    .on("mouseout", function(d) {
                        div.transition()
                        .duration(500)
                        .style("opacity", 0);
                    });
    });
} else {
    document.getElementById("featArea").innerHTML = "No Features File Loaded"
}

//Variants
if (variantsExist === true) {
    var varSvgContainer = d3.select("#varArea")
        .append("svg")
        .attr("width", width)
        .attr("height", 9);

    d3.json(varJsonLocation, function(error, data) {
        data.forEach(function(d) {
            d.contigName = d.contigName;
            d.start = +d.start;
            d.end = +d.end;
            d.track = +d.track;
            d.alleles = d.alleles;
            
        });

        // Add the rectangles
        varSvgContainer.selectAll("rect").data(data)
            .enter()
                .append("g")
                .append("rect")
                    .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
                    .attr("y", 5)
                    .attr("fill", function(d) {
                        if (d.alleles === "Ref / Alt") {
                            return '#00FFFF'; //CYAN
                        } else if (d.alleles === "Alt / Alt") {
                            return '#FF66FF'; //MAGENTA
                        } else if (d.reference === "Ref / Ref") { 
                            return '#99FF33'; //NEON GREEN
                        } else {
                            return '#FFFF66'; //YELLOW
                        }
                    })
                    .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }))
                    .attr("height", (trackHeight-2))
                    .on("mouseover", function(d) {
                        div.transition()
                        .duration(200)
                        .style("opacity", .9);
                        div .html(d.alleles)
                        .style("left", (d3.event.pageX) + "px")
                        .style("top", (d3.event.pageY - 28) + "px");
                    })
                    .on("mouseout", function(d) {
                        div.transition()
                        .duration(500)
                        .style("opacity", 0);
                    });
    });
} else {
    document.getElementById("varArea").innerHTML = "No Variants File Loaded"
}

//Reads
if (readsExist === true) {
    var svgContainer = d3.select("#readsArea")
        .append("svg")
        .attr("height", (height+base))
        .attr("width", width);

    d3.json(readJsonLocation,function(error, data) {
        data.forEach(function(d) {
            d.readName = d.readName;
            d.start = +d.start;
            d.end = +d.end;
            d.track = +d.track;
        });

        // Add the rectangles
        svgContainer.selectAll("rect").data(data)
            .enter()
                .append("g")
                .append("rect")
                    .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
                    .attr("y", (function(d) { return height - trackHeight * (d.track+1); }))
                    .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }))
                    .attr("height", (trackHeight-2))
                    .attr("fill", "steelblue")
                    .on("mouseover", function(d) {
                        div.transition()
                        .duration(200)
                        .style("opacity", .9);
                        div .html(d.readName)
                        .style("left", (d3.event.pageX) + "px")
                        .style("top", (d3.event.pageY - 28) + "px");
                    })
                    .on("mouseout", function(d) {
                        div.transition()
                        .duration(500)
                        .style("opacity", 0);
                    });
    });

    // Add the axis to the container
    svgContainer.append("g")
        .attr("class", "axis")
        .attr("transform", "translate(0, " + height + ")")
        .call(xAxis);
} else {
    document.getElementById("readsArea").innerHTML = "No Reads File Loaded"
}

// Try to move very far left
function moveVeryFarLeft() {
    var newStart = Math.max(0, viewRegStart - (viewRegEnd-viewRegStart));
    var newEnd = Math.max(newStart, viewRegEnd - (viewRegEnd-viewRegStart));
    update(newStart, newEnd);
}

// Try to move far left
function moveFarLeft() {
    var newStart = Math.max(0, viewRegStart - Math.floor((viewRegEnd-viewRegStart)/2));
    var newEnd = Math.max(newStart, viewRegEnd - Math.floor((viewRegEnd-viewRegStart)/2));
    update(newStart, newEnd);
}

// Try to move left
function moveLeft() {
    var newStart = Math.max(0, viewRegStart - Math.floor((viewRegEnd-viewRegStart)/4));
    var newEnd = Math.max(newStart, viewRegEnd - Math.floor((viewRegEnd-viewRegStart)/4));
    update(newStart, newEnd);
}

 // Try to move right
 function moveRight() {
     var newStart = viewRegStart + Math.floor((viewRegEnd-viewRegStart)/4);
     var newEnd = viewRegEnd + Math.floor((viewRegEnd-viewRegStart)/4);
     update(newStart, newEnd);
 }

// Try to move far right
function moveFarRight() {
    var newStart = viewRegStart + Math.floor((viewRegEnd-viewRegStart)/2);
    var newEnd = viewRegEnd + Math.floor((viewRegEnd-viewRegStart)/2);
    update(newStart, newEnd);
}

// Try to move very far right
function moveVeryFarRight() {
    var newStart = viewRegStart + (viewRegEnd-viewRegStart);
    var newEnd = viewRegEnd + (viewRegEnd-viewRegStart);
    update(newStart, newEnd);
}

// Try to zoom in
function zoomIn() {
    var newStart = viewRegStart + Math.floor((viewRegEnd-viewRegStart)/4);
    var newEnd = viewRegEnd - Math.floor((viewRegEnd-viewRegStart)/4);
    update(newStart, newEnd);
}

// Try to zoom out
function zoomOut() {
    var newStart = Math.max(0, viewRegStart - Math.floor((viewRegEnd-viewRegStart)/2));
    var newEnd = viewRegEnd + Math.floor((viewRegEnd-viewRegStart)/2);
    update(newStart, newEnd);
}

// Redirect based on form input
function checkForm(form) {
    var newStart = Math.max(0, form.start.value);
    var newEnd = Math.max(newStart, form.end.value);
    form.reset();
    update(newStart, newEnd);
}

function update(newStart, newEnd) {
    //update all region start and endings
    viewRegStart = newStart;
    viewRegEnd = newEnd;
    viewRegStart = newStart;
    viewRegEnd = newEnd;
    viewRegStart = newStart;
    viewRegEnd = newEnd;
    var numTracks = 0;

    //updating fetch locations
    readJsonLocation = ("/reads/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd);
    referenceStringLocation = "/reference/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
    varJsonLocation = "/variants/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd; //matching read region
    featureJsonLocation = "/features/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;

    //Update Region Info
    d3.select("h2")
        .text("current region: " + viewRefName + ": "+ viewRegStart + "-" + viewRegEnd);
    
    //Updating Reference
    refContainer.selectAll("g").remove();

    // Recreate the scale for the axis
    axisScale = d3.scale.linear()
        .domain([viewRegStart, viewRegEnd])
        .range([0, width]);

    // Recreate the axis
    xAxis = d3.svg.axis()
       .scale(axisScale);

    // Add the axis to the container
    refContainer.append("g")
        .attr("class", "axis")
        .call(xAxis);

    //Updating reference rectangles
    d3.json(referenceStringLocation, function(error, data) {
        refContainer.selectAll("rect").data(data)
        .enter()
            .append("g")
            .append("rect")
                .attr("x", function(d, i) {
                    return i/(viewRegEnd-viewRegStart) * width;
                })
                .attr("y", 30)
                .attr("fill", function(d, i) {
                    if (d.reference === "G") {
                        return '#296629'; //DARK GREEN
                    } else if (d.reference === "C") {
                        return '#CC2900'; //RED
                    } else if (d.reference === "A") { 
                        return '#0066FF'; //BLUE
                    } else {
                        return '#FF6600'; //ORANGE
                    }
                })
                .attr("width", function(d) {
                    return Math.max(1, width/(viewRegEnd-viewRegStart));
                })
                .attr("height", 10)
                .on("mouseover", function(d) {
                    div.transition()
                        .duration(200)
                        .style("opacity", .9);
                    div.html(d.reference)
                        .style("left", (d3.event.pageX - 10) + "px")
                        .style("top", (d3.event.pageY - 30) + "px");
                });
    });

    //Updating Features
    if (featuresExist === true) {
        d3.json(featureJsonLocation, function(error, data) {
            data.forEach(function(d) {
                d.featureId = d.featureId;
                d.featureType = d.featureType;
                d.start = +d.start;
                d.end = +d.end;
                d.track = +d.track;        
            });
            
            //remove all current elements
            featureSvgContainer.selectAll("g").remove();

            // Add the rectangles
            featureSvgContainer.selectAll("rect").data(data)
                .enter()
                    .append("g")
                    .append("rect")
                        .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
                        .attr("y", 5)
                        .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }))
                        .attr("height", (trackHeight-2))
                        .attr("fill", "#6600CC")
                        .on("mouseover", function(d) {
                            div.transition()
                            .duration(200)
                            .style("opacity", .9);
                            div .html(d.featureId)
                            .style("left", (d3.event.pageX) + "px")
                            .style("top", (d3.event.pageY - 28) + "px");
                        })
                        .on("mouseout", function(d) {
                            div.transition()
                            .duration(500)
                            .style("opacity", 0);
                        });
        });
    }

    //Updating Variants
    if (variantsExist === true) {
        d3.json(varJsonLocation, function(error, data) {
            data.forEach(function(d) {
                d.contigName = d.contigName;
                d.start = +d.start;
                d.end = +d.end;
                d.track = +d.track;
                d.alleles = d.alleles;
                
            });

            varSvgContainer.selectAll("g").remove();

            // Add the rectangles
            varSvgContainer.selectAll("rect").data(data)
                .enter()
                    .append("g")
                    .append("rect")
                        .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
                        .attr("y", 5)
                        .attr("fill", function(d) {
                            if (d.alleles === "Ref / Alt") {
                                return '#00FFFF'; //CYAN
                            } else if (d.alleles === "Alt / Alt") {
                                return '#FF66FF'; //MAGENTA
                            } else if (d.reference === "Ref / Ref") { 
                                return '#99FF33'; //NEON GREEN
                            } else {
                                return '#FFFF66'; //YELLOW
                            }
                        })
                        .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }))
                        .attr("height", (trackHeight-2))
                        .on("mouseover", function(d) {
                            div.transition()
                            .duration(200)
                            .style("opacity", .9);
                            div .html(d.alleles)
                            .style("left", (d3.event.pageX) + "px")
                            .style("top", (d3.event.pageY - 28) + "px");
                        })
                        .on("mouseout", function(d) {
                            div.transition()
                            .duration(500)
                            .style("opacity", 0);
                        });
        });
    }

    //Updating Reads
    if (readsExist === true) {
        d3.json(readJsonLocation, function(error, data) {
            data.forEach(function(d) {
                d.readName = d.readName;
                d.start = +d.start;
                d.end = +d.end;
                if (d.track > numTracks) { numTracks = d.track; }
            });

            height = (numTracks+1) * trackHeight;

            // Change dimensions of the SVG container
            svgContainer.attr("height", (height+base));

            // Remove old content
            svgContainer.selectAll("g").remove();

            // Add the rectangles
            svgContainer.selectAll("rect").data(data)
                .enter()
                    .append("g")
                    .append("rect")
                        .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
                        .attr("y", (function(d) { return height - trackHeight * (d.track+1); }))
                        .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }))
                        .attr("height", (trackHeight-2))
                        .attr("fill", "steelblue")
                        .on("mouseover", function(d) {
                            div.transition()
                            .duration(200)
                            .style("opacity", .9);
                            div .html(d.readName)
                            .style("left", (d3.event.pageX) + "px")
                            .style("top", (d3.event.pageY - 28) + "px");
                        })
                        .on("mouseout", function(d) {
                            div.transition()
                            .duration(500)
                            .style("opacity", 0);
                        });

            // Add the axis to the container
            svgContainer.append("g")
                .attr("class", "axis")
                .attr("transform", "translate(0, " + height + ")")
                .call(xAxis);
        });
    }
}

// Hover box for reads
var div = d3.select("#readsArea")
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);