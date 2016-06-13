if (featuresExist === true) {
  var featureSvgContainer = d3.select("#featArea")
    .append("svg")
      .attr("height", featHeight)
      .attr("width", width);

  renderd3Line(featureSvgContainer, featHeight);
}

function renderFeatures(viewRefName, viewRegStart, viewRegEnd) {
  var featureJsonLocation = "/features/" + viewRefName + "?start=" + viewRegStart + "&end=" + viewRegEnd;

  // define x axis
  var xAxisScale = xRange(viewRegStart, viewRegEnd, width);

  // Making hover box
  var featDiv = d3.select("#featArea")
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);

  d3.json(featureJsonLocation, function(error, data) {
  if (error) return error;
  if (!isValidHttpResponse(data)) {
    return;
  }
    var rects = featureSvgContainer.selectAll("rect").data(data);
    var modify = rects.transition();

    // reset height based on number of tracks
    var tracks = d3.max(data, function(d) {return d.track}) + 1;
    if (isNaN(tracks)) tracks = 0;

    d3.select("#featArea")
        .select("svg")
          .attr("height", tracks*featHeight);

    modify
      .attr("x", (function(d) { return xAxisScale(d.start); }))
      .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }));

    var newData = rects.enter();
    newData
      .append("g")
      .append("rect")
        .attr("x", (function(d) { return xAxisScale(d.start); }))
        .attr("y", (function(d) { return d.track * featHeight; }))
        .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }))
        .attr("height", featHeight)
        .attr("fill", "#6600CC")
        .on("click", function(d) {
          featDiv.transition()
            .duration(200)
            .style("opacity", .9);
          featDiv.html(
            "Feature Id: " + d.featureId + "<br>" +
            "Feature Type: " + d.featureType + "<br>" +
            "Start: " + d.start + "<br>" +
            "Track: " + d.track + "<br>" +
            "End: " + d.end)
            .style("left", (d3.event.pageX - 200) + "px")
            .style("top", (d3.event.pageY - 200) + "px");
        })
        .on("mouseover", function(d) {
          featDiv.transition()
          .duration(200)
          .style("opacity", .9);
          featDiv.html(d.featureId)
          .style("left", (d3.event.pageX - 200) + "px")
          .style("top", (d3.event.pageY - 200) + "px");
        })
        .on("mouseout", function(d) {
          featDiv.transition()
          .duration(500)
          .style("opacity", 0);
        });

      var removed = rects.exit();
      removed.remove();
    });
}
