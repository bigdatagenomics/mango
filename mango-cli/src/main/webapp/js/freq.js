// Global Variables
var maxFreq = 0;
var covWidth = $(".sampleCoverage").width();
var height = 100;
var maxFreqs = {}

var svgContainer = {};
for (var i = 0; i < samples.length; i++) {
  var selector = "#" + samples[i] + ">.sampleSummary";
  svgContainer[samples[i]] = d3.select(selector)
    .select(".coverage-svg")
      .attr("height", height)
      .attr("width", width);
}

// Function (accessor function) to return the position for the data that falls just left of the cursor
var bisectData = d3.bisector(function(d) {
  return d.position;
}).left;

function renderCoverage(viewRefName, viewRegStart, viewRegEnd, sampleIds) {

  // Define json location of reads data
  var jsonLocation = "/freq/" + viewRefName + "?start=" + viewRegStart + "&end="
      + viewRegEnd + "&sample=" + sampleIds;

    // Render data for each sample
  d3.json(jsonLocation, function(error, json) {
    if (!isValidHttpResponse(json)) {
      return;
    }

    var frequencyBySample = d3.nest()
      .key(function(d) { return d.sample; })
      .entries(json);

   frequencyBySample.map(function(value) {
      renderJsonCoverage(value);
   });

  });
}

function renderJsonCoverage(data) {
  var sample = filterName(data.key);
  data = data.values
  maxFreq = d3.max(data, function(d) {return d.count});
  maxFreq = typeof maxFreq != "undefined" ? maxFreq : 0;

  data = data.sort(function(a, b){ return d3.ascending(a.position, b.position); })

  // add first and last elements which may be removed from sampling
  if (data[0].position > viewRegStart) {
    data.unshift({position: 0, count: data[0].count});
  }
  if (data[data.length -1].position < viewRegEnd) {
     data.push({position: viewRegEnd, count: data[data.length - 1].count})
  }

  // Create the scale for the x axis
  var xAxisScale = xRange(viewRegStart, viewRegEnd, width);

  // Create the scale for the y axis
  var yAxisScale = d3.scale.linear()
    .domain([maxFreq, 0])
    .range([0, height]);

  // Specify the area for the data being displayed
  var freqArea = d3.svg.area()
    .x(function(d){return xAxisScale(d.position);})
    .y0(height)
    .y1(function(d){return yAxisScale(d.count);})
    .interpolate("basis");

  var removed = svgContainer[sample].selectAll("path").remove()

  // Add the data area shape to the graph
  svgContainer[sample]
    .append("path")
    .attr("d", freqArea(data))
    .style("fill", "#B8B8B8");

}
