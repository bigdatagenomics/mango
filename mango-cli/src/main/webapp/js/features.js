var featureJsonLocation = "/features/" + featureRefName + "?start=" + featureRegStart + "&end=" + featureRegEnd;

var svgContainer = d3.select("#featArea")
    .append("svg")
    .attr("height", (height+base))
    .attr("width", width);

//Add Region Info
d3.select("h2")
    .text("current region: " + featureRefName + ": "+ featureRegStart + "-" + featureRegEnd);

d3.json(featureJsonLocation, function(error, data) {
    data.forEach(function(d) {
        d.featureId = d.featureId;
        d.featureType = d.featureType;
        d.start = +d.start;
        d.end = +d.end;
        d.track = +d.track;        
    });

    // Add the rectangles
    svgContainer.selectAll("rect").data(data)
        .enter()
            .append("g")
            .append("rect")
                .attr("x", (function(d) { return (d.start-featureRegStart)/(featureRegEnd-featureRegStart) * width; }))
                .attr("y", (function(d) { return height - trackHeight * (d.track+1); }))
                .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(featureRegEnd-featureRegStart))); }))
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

// Create the scale for the axis
var axisScale = d3.scale.linear()
    .domain([featureRegStart, featureRegEnd])
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
    var newStart = Math.max(0, featureRegStart - (featureRegEnd-featureRegStart));
    var newEnd = Math.max(newStart, featureRegEnd - (featureRegEnd-featureRegStart));
    update(newStart, newEnd);
}

// Try to move far left
function moveFarLeft() {
    var newStart = Math.max(0, featureRegStart - Math.floor((featureRegEnd-featureRegStart)/2));
    var newEnd = Math.max(newStart, featureRegEnd - Math.floor((featureRegEnd-featureRegStart)/2));
    update(newStart, newEnd);
}

// Try to move left
function moveLeft() {
    var newStart = Math.max(0, featureRegStart - Math.floor((featureRegEnd-featureRegStart)/4));
    var newEnd = Math.max(newStart, featureRegEnd - Math.floor((featureRegEnd-featureRegStart)/4));
    update(newStart, newEnd);
}

 // Try to move right
 function moveRight() {
     var newStart = featureRegStart + Math.floor((featureRegEnd-featureRegStart)/4);
     var newEnd = featureRegEnd + Math.floor((featureRegEnd-featureRegStart)/4);
     update(newStart, newEnd);
 }

// Try to move far right
function moveFarRight() {
    var newStart = featureRegStart + Math.floor((featureRegEnd-featureRegStart)/2);
    var newEnd = featureRegEnd + Math.floor((featureRegEnd-featureRegStart)/2);
    update(newStart, newEnd);
}

// Try to move very far right
function moveVeryFarRight() {
    var newStart = featureRegStart + (featureRegEnd-featureRegStart);
    var newEnd = featureRegEnd + (featureRegEnd-featureRegStart);
    update(newStart, newEnd);
}

// Try to zoom in
function zoomIn() {
    var newStart = featureRegStart + Math.floor((featureRegEnd-featureRegStart)/4);
    var newEnd = featureRegEnd - Math.floor((featureRegEnd-featureRegStart)/4);
    update(newStart, newEnd);
}

// Try to zoom out
function zoomOut() {
    var newStart = Math.max(0, featureRegStart - Math.floor((featureRegEnd-featureRegStart)/2));
    var newEnd = featureRegEnd + Math.floor((featureRegEnd-featureRegStart)/2);
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
    featureRegStart = newStart;
    featureRegEnd = newEnd;
    featureJsonLocation = "/features/" + featureRefName + "?start=" + featureRegStart + "&end=" + featureRegEnd;
    var numTracks = 0;

    //Add Region Info
    d3.select("h2")
        .text("current region: " + featureRefName + ": "+ featureRegStart + "-" + featureRegEnd);
    
    d3.json(featureJsonLocation, function(error, data) {
        data.forEach(function(d) {
            d.featureId = d.featureId;
            d.featureType = d.featureType;
            d.start = +d.start;
            d.end = +d.end;
            if (d.track > numTracks) { numTracks = d.track; }
        });

        height = (numTracks+1) * trackHeight;

        // Change dimensions of the SVG container
        var svgContainer = d3.select("svg")
                             .attr("height", (height+base));

        // Remove old content
        svgContainer.selectAll("g")
                    .remove();

        // Add the rectangles
        svgContainer.selectAll("rect").data(data)
            .enter()
                .append("g")
                .append("rect")
                    .attr("x", (function(d) { return (d.start-featureRegStart)/(featureRegEnd-featureRegStart) * width; }))
                    .attr("y", (function(d) { return height - trackHeight * (d.track+1); }))
                    .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(featureRegEnd-featureRegStart))); }))
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

        // Recreate the scale for the axis
        var axisScale = d3.scale.linear()
            .domain([featureRegStart, featureRegEnd])
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

// Hover box for reads
var div = d3.select("#featArea")
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);