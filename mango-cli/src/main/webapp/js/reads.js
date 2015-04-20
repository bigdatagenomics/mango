var readJsonLocation = "/reads/" + readRefName + "?start=" + readRegStart + "&end=" + readRegEnd;
// var featureJsonLocation = "/features/" + readRefName + "?start=" + readRegStart + "&end=" readRegEnd;

var refContainer = d3.select("#area1")
    .append("svg")
    .attr("width", width)
    .attr("height", 50);

// d3.text
refContainer.selectAll("rect").data(referenceArray)
    .enter()
        .append("g")
        .append("rect")
            .attr("x", function(d, i) {
                return i/(readRegEnd-readRegStart) * width;
            })
            .attr("y", 30)
            .attr("fill", function(d, i) {
                if (d === "G") {
                    return '#336600';
                } else if (d === "C") {
                    return '#36B2AB';
                } else if (d === "A") {
                    return '#0066FF';
                } else {
                    return '#99FF99';
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
                div.html(d)
                    .style("left", (d3.event.pageX - 10) + "px")
                    .style("top", (d3.event.pageY - 30) + "px");
            });

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
    readRegStart = newStart;
    readRegEnd = newEnd;
    readJsonLocation = ("/reads/" + readRefName + "?start=" + readRegStart + "&end=" + readRegEnd);
    var numTracks = 0;

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
        var svgContainer = d3.select("svg")
            .attr("height", (height+base));

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