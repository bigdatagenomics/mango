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
  d3.json(jsonLocation, function(error, ret) {
    if (!isValidHttpResponse(ret)) {
      return;
    }
    var data = ret.map(JSON.parse);

    var frequencyBySample = d3.nest()
      .key(function(d) { return d.sample; })
      .entries(data);

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
  svgContainer[sample].append("rect")
    .attr("width", width)
    .attr("x", 0)
    .attr("height", height)
    .style("fill", "none")
    .style("pointer-events", "all")
    .on("mouseover", function() { focus.style("display", null); })
    .on("mouseout", function() { focus.style("display", "none"); })
    .on("mousemove", mousemove);

  // What we use to add tooltips
  var focus = svgContainer[sample].append("g")
    .style("display", "none");

  // Append the y guide line
  focus.append("line")
    .attr("class", "yGuide")
    .style("stroke", "red")
    .style("stroke-dasharray", "3,3")
    .style("opacity", 0.5)
    .attr("x1", 0)
    .attr("x2", width);

  // Text above line to display reference position
  focus.append("text")
    .attr("class", "above")
    .style("stroke", "black")
    .style("font-size", "12")
    .style("stroke-width", "1px")
    .style("opacity", 0.8)
    .attr("dx", 8)
    .attr("dy", "-.3em");

  // Text below line to display coverage
  focus.append("text")
    .attr("class", "below")
    .style("stroke", "black")
    .style("font-size", "12")
    .style("stroke-width", "1px")
    .style("opacity", 0.8)
    .attr("dx", 8)
    .attr("dy", "1em");

  function mousemove() {
    // Initial calibrates initial mouse offset due to y axis position
    var x0 = xAxisScale.invert(d3.mouse(this)[0]);
    var i = bisectData(data, x0, 1);
    var opt1 = data[i - 1];
    var opt2 = data[i];

    if (data.length == 0) {
      return
    }

    // Finds the position that is closest to the mouse cursor
    var d = (x0 - opt1.position) > (opt2.position - x0) ? opt2 : opt1;

    focus.select("text.above")
      .attr("transform",
        "translate(" + xAxisScale(d.position) + "," +
        yAxisScale(d.count) + ")")
      .text("Position: " + d.position);

    focus.select("text.below")
      .attr("transform",
        "translate(" + xAxisScale(d.position) + "," +
        yAxisScale(d.count) + ")")
      .text("Freq: " + d.count);

    focus.select(".yGuide")
      .attr("transform",
        "translate(" + (width - 50) * -1 + "," +
          yAxisScale(d.count) + ")")
      .attr("x2", width + width);
  }

}
