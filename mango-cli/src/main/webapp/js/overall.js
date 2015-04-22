var readJsonLocation = "/reads/" + readRefName + "?start=" + readRegStart + "&end=" + readRegEnd;
var referenceStringLocation = "/reference/" + readRefName + "?start=" + readRegStart + "&end=" + readRegEnd;
var varJsonLocation = "/variants/" + varRefName + "?start=" + varRegStart + "&end=" + varRegEnd;
var featureJsonLocation = "/features/" + featureRefName + "?start=" + featureRegStart + "&end=" + featureRegEnd;

//Reference
var refContainer = d3.select("#area1")
    .append("svg")
    .attr("width", width)
    .attr("height", 50);

d3.json(referenceStringLocation, function(error, data) {
    refContainer.selectAll("rect").data(data)
    .enter()
        .append("g")
        .append("rect")
            .attr("x", function(d, i) {
                return i/(readRegEnd-readRegStart) * width;
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
                return Math.max(1, width/(readRegEnd-readRegStart));
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
var featureSvgContainer = d3.select("#area2")
    .append("svg")
    .attr("height", 50)
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
                .attr("x", (function(d) { return (d.start-featureRegStart)/(featureRegEnd-featureRegStart) * width; }))
                .attr("y", 30)
                .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(featureRegEnd-featureRegStart))); }))
                .attr("height", (trackHeight-2))
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

//Variants
var varSvgContainer = d3.select("#area3")
    .append("svg")
    .attr("width", width)
    .attr("height", 50);

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
                .attr("x", (function(d) { return (d.start-varRegStart)/(varRegEnd-varRegStart) * width; }))
                .attr("y", 30)
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
                .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(varRegEnd-varRegStart))); }))
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

//Reads
var svgContainer = d3.select("body")
    .append("svg")
    .attr("height", (height+base))
    .attr("width", width);

d3.select("h2")
    .append("span")
    .text(readRegStart + "-" + readRegEnd);

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
                .attr("x", (function(d) { return (d.start-readRegStart)/(readRegEnd-readRegStart) * width; }))
                .attr("y", (function(d) { return height - trackHeight * (d.track+1); }))
                .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(readRegEnd-readRegStart))); }))
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

// Create the scale for the axis
var axisScale = d3.scale.linear()
    .domain([readRegStart, readRegEnd])
    .range([0, width]);

// Create the axis
var xAxis = d3.svg.axis()
   .scale(axisScale)
   .ticks(5);

// Add the axis to the container
svgContainer.append("g")
    .attr("class", "axis")
    .attr("transform", "translate(0, " + height + ")")
    .call(xAxis);

// Try to move very far left
function moveVeryFarLeft() {
    var newStart = Math.max(0, readRegStart - (readRegEnd-readRegStart));
    var newEnd = Math.max(newStart, readRegEnd - (readRegEnd-readRegStart));
    update(newStart, newEnd);
}

// Try to move far left
function moveFarLeft() {
    var newStart = Math.max(0, readRegStart - (readRegEnd-readRegStart)/2);
    var newEnd = Math.max(newStart, readRegEnd - (readRegEnd-readRegStart)/2);
    update(newStart, newEnd);
}

// Try to move left
function moveLeft() {
    var newStart = Math.max(0, readRegStart - (readRegEnd-readRegStart)/4);
    var newEnd = Math.max(newStart, readRegEnd - (readRegEnd-readRegStart)/4);
    update(newStart, newEnd);
}

 // Try to move right
 function moveRight() {
     var newStart = readRegStart + (readRegEnd-readRegStart)/4;
     var newEnd = readRegEnd + (readRegEnd-readRegStart)/4;
     update(newStart, newEnd);
 }

// Try to move far right
function moveFarRight() {
    var newStart = readRegStart + (readRegEnd-readRegStart)/2;
    var newEnd = readRegEnd + (readRegEnd-readRegStart)/2;
    update(newStart, newEnd);
}

// Try to move very far right
function moveVeryFarRight() {
    var newStart = readRegStart + (readRegEnd-readRegStart);
    var newEnd = readRegEnd + (readRegEnd-readRegStart);
    update(newStart, newEnd);
}

// Try to zoom in
function zoomIn() {
    var newStart = readRegStart + (readRegEnd-readRegStart)/4;
    var newEnd = readRegEnd - (readRegEnd-readRegStart)/4;
    update(newStart, newEnd);
}

// Try to zoom out
function zoomOut() {
    var newStart = Math.max(0, readRegStart - (readRegEnd-readRegStart)/2);
    var newEnd = readRegEnd - (readRegEnd-readRegStart)/2;
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
    readRegStart = newStart;
    readRegEnd = newEnd;
    varRegStart = newStart;
    varRegEnd = newEnd;
    featureRegStart = newStart;
    featureRegEnd = newEnd;
    var numTracks = 0;

    //updating fetch locations
    readJsonLocation = ("/reads/" + readRefName + "?start=" + readRegStart + "&end=" + readRegEnd);
    referenceStringLocation = "/reference/" + readRefName + "?start=" + readRegStart + "&end=" + readRegEnd;
    varJsonLocation = "/variants/" + varRefName + "?start=" + varRegStart + "&end=" + varRegEnd; //matching read region
    featureJsonLocation = "/features/" + featureRefName + "?start=" + featureRegStart + "&end=" + featureRegEnd;

    //Updating Reference
    refContainer.selectAll("g").remove();
    d3.json(referenceStringLocation, function(error, data) {
        refContainer.selectAll("rect").data(data)
        .enter()
            .append("g")
            .append("rect")
                .attr("x", function(d, i) {
                    return i/(readRegEnd-readRegStart) * width;
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
                    return Math.max(1, width/(readRegEnd-readRegStart));
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
                    .attr("x", (function(d) { return (d.start-featureRegStart)/(featureRegEnd-featureRegStart) * width; }))
                    .attr("y", 30)
                    .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(featureRegEnd-featureRegStart))); }))
                    .attr("height", (trackHeight-2))
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

    //Updating Variants
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
                    .attr("x", (function(d) { return (d.start-varRegStart)/(varRegEnd-varRegStart) * width; }))
                    .attr("y", 30)
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
                    .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(varRegEnd-varRegStart))); }))
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


    //Updating Reads
    d3.json(readJsonLocation, function(error, data) {
        data.forEach(function(d) {
            d.readName = d.readName;
            d.start = +d.start;
            d.end = +d.end;
            if (d.track > numTracks) { numTracks = d.track; }
        });

        d3.select("h2")
          .select("span")
          .text(readRegStart + "-" + readRegEnd);

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
                    .attr("x", (function(d) { return (d.start-readRegStart)/(readRegEnd-readRegStart) * width; }))
                    .attr("y", (function(d) { return height - trackHeight * (d.track+1); }))
                    .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(readRegEnd-readRegStart))); }))
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

        // Recreate the scale for the axis
        var axisScale = d3.scale.linear()
            .domain([readRegStart, readRegEnd])
            .range([0, width]);

        // Recreate the axis
        var xAxis = d3.svg.axis()
           .scale(axisScale)
           .ticks(5);

        // Add the axis to the container
        svgContainer.append("g")
            .attr("class", "axis")
            .attr("transform", "translate(0, " + height + ")")
            .call(xAxis);
    });
}

// // Hover box for reads
var div = d3.select("body")
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);