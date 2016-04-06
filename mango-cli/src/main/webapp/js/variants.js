var varJsonLocation = "/variants/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
var varFreqJsonLocation = "/variantfreq/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;

// Section Heights
var refHeight = 38;
var varHeight = 0; //Default variable: this will change based on number of tracks
var freqHeight = 200;
var width = $("#varArea").width();


// Svg container for variant frequency
var svg = d3.select("#varFreqArea")
  .append("svg")
  .attr("width", width)
  .attr("height", freqHeight);

// Functions
function renderVariants(refName, start, end) {
  varJsonLocation = "/variants/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
  varFreqJsonLocation = "/variantfreq/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;
  renderJsonVariants();
}
function renderVariantFrequency(refName, start, end) {
  renderVariantFrequency();

}
var varSvgContainer = d3.select("#varArea")
  .append("svg")
    .attr("width", width)
    .attr("height", varHeight);


// Making hover box
var varDiv = d3.select("#varArea")
  .append("div")
  .attr("class", "tooltip")
  .style("opacity", 0);


function renderJsonVariants() {

  d3.json(varJsonLocation, function(error, data) {
    if (error) throw error;
    if (!isValidHttpResponse(data)) {
      return;
    }

    //dynamically setting height of svg containers
    var numTracks = d3.max(data, function(d) {return d.track});
    var varTrackHeight = getTrackHeight();
    varHeight = (numTracks+1)*varTrackHeight;
    varSvgContainer.attr("height", varHeight);
    renderd3Line(varSvgContainer, varHeight);

    // Add the rectangles
    var variants = varSvgContainer.selectAll(".variant").data(data);
    var modify = variants.transition();
    modify
      .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
      .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }));

    var newData = variants.enter();
    newData
      .append("g")
      .append("rect")
        .attr("class", "variant")
        .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
        .attr("y", (function(d) { return varHeight - varTrackHeight * (d.track+1);}))
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
        .attr("height", varTrackHeight)
        .on("click", function(d) {
          varDiv.transition()
            .duration(200)
            .style("opacity", .9);
          varDiv.html(
            "Contig: " + d.contigName + "<br>" +
            "Alleles: " + d.alleles)
            .style("left", d3.event.pageX + 10 + "px")
            .style("top", d3.event.pageY - 100 +  "px");
        })
        .on("mouseover", function(d) {
          varDiv.transition()
            .duration(200)
            .style("opacity", .9);
          varDiv.html(d.alleles)
            .style("left", d3.event.pageX + 10 +  "px")
            .style("top", d3.event.pageY - 100 +  "px");
        })
        .on("mouseout", function(d) {
          varDiv.transition()
          .duration(500)
          .style("opacity", 0);
        });

    var removed = variants.exit();
    removed.remove();
  });
}

function renderVariantFrequency() {
  // Making hover box
  var varDiv = d3.select("#varFreqArea")
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);

  renderd3Line(varDiv, varHeight);

  var margin = {top: 20, right: 0, bottom: 30, left: 0},
      height = 200 - margin.top - margin.bottom;

  // var x = d3.scale.ordinal()
  //     .rangeRoundBands([0, width], .1);
  var x = d3.scale.linear()
    .domain([viewRegStart, viewRegEnd])    
    .range([0, width]);

  var y = d3.scale.linear()
    .range([height, 0]);

  var xAxis = d3.svg.axis()
    .scale(x)
    .orient("bottom");

  var yAxis = d3.svg.axis()
    .scale(y)
    .orient("left")
    .ticks(10, "%");

  d3.json(varFreqJsonLocation, function(error, data) {
    if (error) throw error;
    if (!isValidHttpResponse(data)) {
      return;
    }

    x.domain([viewRegStart, viewRegEnd]);
    y.domain([0, d3.max(data, function(d) { return d.count; })]);

    svg.select(".axis").remove();
    svg.append("g")
      .attr("class", "x axis")
      .attr("transform", "translate(0," + height + ")")
      .call(xAxis);

    svg.append("g")
        .attr("class", "y axis")
        .call(yAxis)
      .append("text")
        .attr("transform", "rotate(-90)")
        .attr("y", 6)
        .attr("dy", ".71em")
        .style("text-anchor", "end")
        .text("Frequency");

    var freqBars = svg.selectAll(".bar").data(data);

    var modifyBars = freqBars.transition();
    modifyBars
      .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
      .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }));

    var newBars = freqBars.enter();
    newBars.append("rect")
      .attr("class", "bar")
      .attr("fill", '#2E6DA4')
      .attr("x", (function(d) { return (d.start-viewRegStart)/(viewRegEnd-viewRegStart) * width; }))
      .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }))
      .attr("y", function(d) { return y(d.count); })
      .attr("height", function(d) { return height - y(d.count); })
      .on("mouseover", function(d) {
        varDiv.transition()
          .duration(200)
          .style("opacity", .9);
        varDiv.html("Samples with variant: " + d.count)
          .style("left", (d3.event.pageX - 120) + "px")
          .style("top", (d3.event.pageY - 28) + "px");
      })
      .on("mouseout", function(d) {
        varDiv.transition()
        .duration(500)
        .style("opacity", 0);
      });
    var removedBars = freqBars.exit();
    removedBars.remove();
    
  });
}
