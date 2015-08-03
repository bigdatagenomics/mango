// Global Variables
var maxFreq = 0;
var jsonLocation = "/freq/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
var width = window.innerWidth - 50;
var base = 50;
var height = window.innerHeight;

//Add Region Info
d3.select("h2")
  .text("current region: " + viewRefName + ": "+ viewRegStart + "-" + viewRegEnd);

var svgContainer = d3.select("body")
  .append("svg")
  .attr("height", (height+base))
  .attr("width", (width+base));

render(viewRegStart, viewRegEnd);

function render(start, end) {
  viewRegStart = start;
  viewRegEnd = end;

  var jsonLocation = "/freq/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;

  // Removes at first to update frequency graph
  svgContainer.selectAll("g").remove()

  //Add Region Info
  d3.select("h2")
    .text("current region: " + viewRefName + ": "+ viewRegStart + "-" + viewRegEnd);

  d3.json(jsonLocation, function(error, data) {
    maxFreq = d3.max(data, function(d) {return d.freq});

    // Create the scale for the x axis
    var xAxisScale = d3.scale.linear()
      .domain([viewRegStart, viewRegEnd])
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

    // Create the scale for the data
    var dataScale = d3.scale.linear()
      .domain([0, maxFreq])
      .range([0, height]);

    var freqArea = d3.svg.area()
      .x(function(d){return base + (d.base-viewRegStart)/(viewRegEnd-viewRegStart) * width;})
      .y0(height)
      .y1(function(d){return dataScale(maxFreq-d.freq);})

    svgContainer.append("g")
      .append("path")
      .attr("d", freqArea(data))
      .style("fill", "steelblue")

  });

}

// Try to move very far left
function moveVeryFarLeft() {
  var newStart = Math.max(0, viewRegStart - (viewRegEnd-viewRegStart));
  var newEnd = Math.max(newStart, viewRegEnd - (viewRegEnd-viewRegStart));
  render(newStart, newEnd);
}

// Try to move far left
function moveFarLeft() {
  var newStart = Math.max(0, viewRegStart - Math.floor((viewRegEnd-viewRegStart)/2));
  var newEnd = Math.max(newStart, viewRegEnd - Math.floor((viewRegEnd-viewRegStart)/2));
  render(newStart, newEnd);
}

// Try to move left
function moveLeft() {
  var newStart = Math.max(0, viewRegStart - Math.floor((viewRegEnd-viewRegStart)/4));
  var newEnd = Math.max(newStart, viewRegEnd - Math.floor((viewRegEnd-viewRegStart)/4));
  render(newStart, newEnd);
}

 // Try to move right
 function moveRight() {
   var newStart = viewRegStart + Math.floor((viewRegEnd-viewRegStart)/4);
   var newEnd = viewRegEnd + Math.floor((viewRegEnd-viewRegStart)/4);
   render(newStart, newEnd);
 }

// Try to move far right
function moveFarRight() {
  var newStart = viewRegStart + Math.floor((viewRegEnd-viewRegStart)/2);
  var newEnd = viewRegEnd + Math.floor((viewRegEnd-viewRegStart)/2);
  render(newStart, newEnd);
}

// Try to move very far right
function moveVeryFarRight() {
  var newStart = viewRegStart + (viewRegEnd-viewRegStart);
  var newEnd = viewRegEnd + (viewRegEnd-viewRegStart);
  render(newStart, newEnd);
}

// Try to zoom in
function zoomIn() {
  var newStart = viewRegStart + Math.floor((viewRegEnd-viewRegStart)/4);
  var newEnd = viewRegEnd - Math.floor((viewRegEnd-viewRegStart)/4);
  render(newStart, newEnd);
}

// Try to zoom out
function zoomOut() {
  var newStart = Math.max(0, viewRegStart - Math.floor((viewRegEnd-viewRegStart)/2));
  var newEnd = viewRegEnd + Math.floor((viewRegEnd-viewRegStart)/2);
  render(newStart, newEnd);
}

// Redirect based on form input
function checkForm(form) {
  var newStart = Math.max(0, form.start.value);
  var newEnd = Math.max(newStart, form.end.value);
  form.reset();
  render(newStart, newEnd);
}
