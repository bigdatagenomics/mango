var jsonLocation = "/variants/" + refName + "?start=" + start + "&end=" + end;

var svgContainer = d3.select("body")
                     .append("svg")
                     .attr("height", (height+base))
                     .attr("width", width);

d3.select("h2")
  .append("span")
  .text(start + "-" + end);

d3.json(jsonLocation, function(error, data) {
    data.forEach(function(d) {
        d.contigName = d.contigName;
        d.start = +d.start;
        d.end = +d.end;
        d.track = +d.track;
        d.alleles = d.alleles;
        
    });

    // Add the rectangles
    svgContainer.append("g")
                .selectAll("rect")
                .data(data)
                .enter()
                .append("rect")
                .attr("x", (function(d) { return (d.start-start)/(end-start) * width; }))
                .attr("y", (function(d) { return height - trackHeight * (d.track+1); }))
                .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(end-start))); }))
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

// Create the scale for the axis
var axisScale = d3.scale.linear()
                        .domain([start, end])
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
    var newStart = Math.max(0, start - (end-start));
    var newEnd = Math.max(newStart, end - (end-start));
    update(newStart, newEnd);
}

// Try to move far left
function moveFarLeft() {
    var newStart = Math.max(0, start - (end-start)/2);
    var newEnd = Math.max(newStart, end - (end-start)/2);
    update(newStart, newEnd);
}

// Try to move left
function moveLeft() {
    var newStart = Math.max(0, start - (end-start)/4);
    var newEnd = Math.max(newStart, end - (end-start)/4);
    update(newStart, newEnd);
}

 // Try to move right
 function moveRight() {
     var newStart = start + (end-start)/4;
     var newEnd = end + (end-start)/4;
     update(newStart, newEnd);
 }

// Try to move far right
function moveFarRight() {
    var newStart = start + (end-start)/2;
    var newEnd = end + (end-start)/2;
    update(newStart, newEnd);
}

// Try to move very far right
function moveVeryFarRight() {
    var newStart = start + (end-start);
    var newEnd = end + (end-start);
    update(newStart, newEnd);
}

// Try to zoom in
function zoomIn() {
    var newStart = start + (end-start)/4;
    var newEnd = end - (end-start)/4;
    update(newStart, newEnd);
}

// Try to zoom out
function zoomOut() {
    var newStart = Math.max(0, start - (end-start)/2);
    var newEnd = end - (end-start)/2;
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
    start = newStart;
    end = newEnd;
    jsonLocation = ("/variants/" + refName + "?start=" + start + "&end=" + end);
    var numTracks = 0;

    d3.json(jsonLocation, function(error, data) {
        data.forEach(function(d) {
            d.readName = d.readName;
            d.start = +d.start;
            d.end = +d.end;
            d.alleles = d.alleles;
            if (d.track > numTracks) { numTracks = d.track; }
        });

        d3.select("h2")
          .select("span")
          .text(start + "-" + end);

        height = (numTracks+1) * trackHeight;

        // Change dimensions of the SVG container
        var svgContainer = d3.select("svg")
                             .attr("height", (height+base));

        // Remove old content
        svgContainer.selectAll("g")
                    .remove();

        // Add the rectangles
        svgContainer.append("g")
                    .selectAll("rect")
                    .data(data)
                    .enter()
                    .append("rect")
                    .attr("x", (function(d) { return (d.start-start)/(end-start) * width; }))
                    .attr("y", (function(d) { return height - trackHeight * (d.track+1); }))
                    .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(end-start))); }))
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

        // Recreate the scale for the axis
        var axisScale = d3.scale.linear()
                                .domain([start, end])
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
var div = d3.select("body")
            .append("div")
            .attr("class", "tooltip")
            .style("opacity", 0);