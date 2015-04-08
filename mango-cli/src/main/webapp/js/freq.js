var maxFreq = 0;
var jsonLocation = "/freq/" + refName + "?start=" + start + "&end=" + end;

d3.select("h2")
  .append("span")
  .text(start + "-" + end);

d3.json(jsonLocation, function(error, data) {
    data.forEach(function(d) {
        d.base = +d.base;
        d.freq = +d.freq;
        if (d.freq > maxFreq) { maxFreq = d.freq; }
    });

    var svgContainer = d3.select("body")
                         .append("svg")
                         .attr("height", (height+base))
                         .attr("width", (width+base));

    // Create the scale for the data
    var dataScale = d3.scale.linear()
                            .domain([0, maxFreq])
                            .range([0, height]);

    var freqline = d3.svg.line()
                     .x(function(d){return base + (d.base-start)/(end-start) * width;})
                     .y(function(d){return dataScale(maxFreq-d.freq);})
                     .interpolate("basis");

    svgContainer.append("g")
                .append("svg:path")
                .attr("d", freqline(data))
                .style("stroke-width", 2)
                .style("stroke", "steelblue")
                .style("fill", "none");

    // Create the scale for the x axis
    var xAxisScale = d3.scale.linear()
                            .domain([start, end])
                            .range([0, width]);

    // Create the scale for the y axis
    var yAxisScale = d3.scale.linear()
                            .domain([maxFreq, 0])
                            .range([0, height]);

    // Create the x axis
    var xAxis = d3.svg.axis()
                   .scale(xAxisScale)
                   .ticks(5);

    // Create the y axis
    var yAxis = d3.svg.axis()
                   .scale(yAxisScale)
                   .orient("left")
                   .ticks(5);

    // Add the x axis to the container
    svgContainer.append("g")
                .attr("class", "axis")
                .attr("transform", "translate(" + base + ", " + height + ")")
                .call(xAxis);

    // Add the y axis to the container
    svgContainer.append("g")
                .attr("class", "axis")
                .attr("transform", "translate(" + base + ", 0)")
                .call(yAxis);
});

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
    jsonLocation = ("/freq/" + refName + "?start=" + start + "&end=" + end);
    maxFreq = 0;

    d3.json(jsonLocation, function(error, data) {
        data.forEach(function(d) {
            d.base = +d.base;
            d.freq = +d.freq;
            if (d.freq > maxFreq) { maxFreq = d.freq; }
        });

        d3.select("h2")
                      .select("span")
                      .text(start + "-" + end);


        // Create the scale for the data
        var dataScale = d3.scale.linear()
                                .domain([0, maxFreq])
                                .range([0, height]);

        var freqline = d3.svg.line()
                         .x(function(d){return base + (d.base-start)/(end-start) * width;})
                         .y(function(d){return dataScale(maxFreq-d.freq);})
                         .interpolate("basis");

        // Change dimensions of the SVG container
        var svgContainer = d3.select("svg")
                             .attr("height", (height+base));

        // Remove old content
        svgContainer.selectAll("g")
                    .remove();

        // Add the path
        svgContainer.append("g")
                    .append("path")
                    .attr("d", freqline(data))
                    .style("stroke-width", 2)
                    .style("stroke", "steelblue")
                    .style("fill", "none");

        // Create the scale for the x axis
        var xAxisScale = d3.scale.linear()
                                .domain([start, end])
                                .range([0, width]);

        // Create the scale for the y axis
        var yAxisScale = d3.scale.linear()
                                .domain([maxFreq, 0])
                                .range([0, height]);

        // Create the x axis
        var xAxis = d3.svg.axis()
                       .scale(xAxisScale)
                       .ticks(5);

        // Create the y axis
        var yAxis = d3.svg.axis()
                       .scale(yAxisScale)
                       .orient("left")
                       .ticks(5);

        // Add the x axis to the container
        svgContainer.append("g")
                    .attr("class", "axis")
                    .attr("transform", "translate(" + base + ", " + height + ")")
                    .call(xAxis);

        // Add the y axis to the container
        svgContainer.append("g")
                    .attr("class", "axis")
                    .attr("transform", "translate(" + base + ", 0)")
                    .call(yAxis);
    });
}