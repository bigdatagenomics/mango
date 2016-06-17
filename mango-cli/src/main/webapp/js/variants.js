// Section Heights
var varHeight = 100; //Default variable: this will change based on number of tracks
var freqHeight = 100;

var varSvgClass = "varSvg";

//contains the svg containers for all summary elements
var summarySvgContainer = {};

var varSvgContainer = {};

var varSummaryData = [];

//Manages changes when clicking checkboxes
d3.selectAll("input").on("change", checkboxChange);

for (var i = 0; i < varFiles.length; i++) {

  var selector = "#" + varFiles[i] + ">.variantSummary";
  summarySvgContainer[varFiles[i]] = d3.select(selector)
    .select("svg")
    .attr("height", trackHeight)
    .attr("width", width);
}


function renderVariantSummary(refName, start, end) {
  startWait("varArea");

  var varJsonLocation = "/variants/" + viewRefName + "?start=" + viewRegStart + "&end="
    + viewRegEnd  + "&sample=" + varFiles;

  // Render data for each sample
  d3.json(varJsonLocation,function(error, json) {
    if (error != null) {
      if (error.status == 413)  // entity too large
        json.variants = "";
    }

    for (var i = 0; i < varFiles.length; i++) {
      if (json.variants == "") {
        summarySvgContainer[varFiles[i]].selectAll("g").remove();
      } else {
        var data = JSON.parse(json["freq"]);
        data = typeof data[varFiles[i]] != "undefined" ? data[varFiles[i]] : [];
        renderd3Line(summarySvgContainer[varFiles[i]], height);
        varSummaryData[i] = [];
        //data from backend now split between freq and variants
        varSummaryData[i]= data;

        renderVariantFreq(varSummaryData[i], varFiles[i])
      }
    }
  });
  stopWait("varArea");
  var keys = Object.keys(varSvgContainer);
  keys.forEach(function(varFile) {
    var checkSelector = "#viewVariants" + varFile;
    if ($(checkSelector).is(':checked')) {
      renderRawVariants(refName, start, end, varFile);
    }
  });
}

function renderRawVariants(refName, start, end, varFile) {

  var isData = varFile in varSvgContainer;
  if (isData) {
    var region = varSvgContainer[varFile];
    if (region.refName == refName && region.start == start && region.end == end) {
      return;
    }
  }

  varSvgContainer[varFile] = {
    refName: refName,
    start: start,
    end: end
  };

  // Define json location of reads data
  var rawVarJsonLocation = "/variants/" + viewRefName + "?start=" + viewRegStart + "&end="
    + viewRegEnd  + "&sample=" + varFile + "&isRaw=true";

  d3.json(rawVarJsonLocation,function(error, json) {
    if (error) return error;
    if (!isValidHttpResponse(json)) {
      return;
    }
    var varData = JSON.parse(json["variants"]);
    varData = typeof varData[varFile] != "undefined" ? varData[varFile] : [];
    renderVariantsByResolution(varData, varFile);
  });

}


function renderVariantsByResolution(data, varFile) {
  // Render xaxis
  var varDiv = [];

  var selector = getVariantSelector(varFile);

  var container = d3.select(selector)
    .select("svg")
    .attr("class", varSvgClass)
    .attr("height", (varHeight))
    .attr("width", width);

  container.selectAll("g").remove();

  varDiv[i] = d3.select(selector)
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);

  var xAxisScale = xRange(viewRegStart, viewRegEnd, width);


  //dynamically setting height of svg containers
  var numTracks = d3.max(data, function(d) {return d.track});
  numTracks = typeof numTracks != "undefined" ? numTracks : [];

  varHeight = (numTracks+1)*trackHeight;
  container.attr("height", varHeight);

  var variants = container.selectAll(".varrect").data(data);

  // Add the rectangles
  var modify = variants.transition();
  modify
   .attr("x", (function(d) { return xAxisScale(d.start); }))
   .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }));

  var newData = variants.enter();
  newData
    .append("g")
    .append("rect")
      .attr("class", "variant")
      .attr("x", (function(d) { return xAxisScale(d.start); }))
      .attr("y", (function(d) { return varHeight - trackHeight * (d.track+1);}))
      .attr("fill", function(d) {
       if (d.alleles === "Ref/Alt") {
        return '#00FFFF'; //CYAN
       } else if (d.alleles === "Alt/Alt") {
        return '#FF66FF'; //MAGENTA
       } else if (d.reference === "Ref/Ref") {
        return '#99FF33'; //NEON GREEN
       } else {
        return '#990000'; //RED
       }
      })
    .attr("width", (function(d) { return Math.max(1,(d.end-d.start)*(width/(viewRegEnd-viewRegStart))); }))
    .attr("height", trackHeight)
    .on("mouseover", function(d) {
      varDiv[i].transition()
        .duration(200)
        .style("opacity", .9);
      varDiv[i].html(
        "SampleId: " + d.sampleId + "<br>" +
        "Position: " + d.start + "<br>" +
        "Track: " + d.track + "<br>" +
        "Alleles: " + d.alleles)
        .style("left", d3.event.pageX + "px")
        .style("top", d3.event.pageY + "px");
    })
    .on("mouseout", function() {
      varDiv[i].transition()
        .duration(500)
        .style("opacity", 0);
    });

  var removed = variants.exit();
  removed.remove();
}


function renderVariantFreq(data, varFile) {
 // Making hover box
  var selector = getVariantSummarySelector(varFile);

  var freqArea = d3.select(selector)
      .select("svg")
      .attr("height", (freqHeight))
      .attr("width", width);

  var freqDiv = d3.select(selector)
    .append("div")
    .attr("class", "tooltip")
    .style("opacity", 0);


  var margin = {top: 20, right: 0, bottom: 30, left: 0},
    height = 200 - margin.top - margin.bottom;

  // Render xaxis
  var xAxisScale = xRange(viewRegStart, viewRegEnd, width);

  var y = d3.scale.linear()
    .range([height, 0]);

  var xAxis = d3.svg.axis()
    .scale(xAxisScale)
    .orient("bottom");

  var yAxis = d3.svg.axis()
    .scale(y)
    .orient("left")
    .ticks(10, "%");

  y.domain([0, d3.max(data, function(d) { return d.total; })]);

  freqArea.append("g")
    .attr("class", "axis")
    .attr("transform", "translate(0," + height + ")")
    .call(xAxis);

  freqArea.append("g")
    .attr("class", "y axis")
    .call(yAxis)
  .append("text")
    .attr("transform", "rotate(-90)")
    .attr("y", 6)
    .attr("dy", ".71em")
    .style("text-anchor", "end")
    .text("Frequency");

  var freqBars = freqArea.selectAll(".bar").data(data);

  var modifyBars = freqBars.transition();
  modifyBars
    .attr("x", (function(d) { return xAxisScale(d.start); }))
    .attr("width", (function() { return Math.max(1, (width/(viewRegEnd-viewRegStart))); }));

  var newBars = freqBars.enter();
  newBars.append("rect")
    .attr("class", "bar")
    .attr("fill", '#2E6DA4')
    .attr("x", (function(d) { return xAxisScale(d.start); }))
    .attr("width", (function() { return Math.max(1, (width/(viewRegEnd-viewRegStart))); }))
    .attr("y", function(d) { return y(d.total); })
    .attr("height", function(d) { return height - y(d.total); })
    .on("mouseover", function(d) {
      freqDiv.transition()
        .duration(200)
        .style("opacity", .9);
      freqDiv.html("Samples with variant: " + d.total + "<br>" +
        "Position: " + d.start)
        .style("left", d3.event.pageX + "px")
        .style("top", d3.event.pageY + "px");
    })
    .on("mouseout", function() {
      freqDiv.transition()
      .duration(500)
      .style("opacity", 0);
    });
  var removedBars = freqBars.exit();
  removedBars.remove();
}
