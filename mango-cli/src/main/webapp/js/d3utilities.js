// width of scrollbar when page content length exceeds display height
var barWidth = 21;
$(".main").width($("body").width() - barWidth);
var width = $(".graphArea").width();

// colors for base pairs [A, T, G, C]
var aColor = '#5050FF'; //AZURE
var cColor = '#E00000'; //CRIMSON
var tColor = '#E6E600'; //TWEETY BIRD
var gColor = '#00C000'; //GREEN
var nColor = '#D3D3D3'; // GREY
var brown = "#47244C";  // BROWN

var baseColors = {
  'A': aColor,
  'C': cColor,
  'T': tColor,
  'G': gColor
};


// mouse loading functionality
function startWait(div) {
    $(div).css( 'cursor', 'wait' );
    $(div).css( 'background-color', 'rgba(204, 204, 204, 0.8)' );

}

// mouse loading functionality
function stopWait(div) {
    $(div).css( 'cursor', 'default' );
    $(div).css( 'background-color', 'white' );

}


// Create the scale for the x axis.
// Used for variants and reference
function xRange(start, end, width){
    return d3.scale.linear()
        .domain([start, end])
        .range([0, width]);
}

// render line for navigation
function renderd3Line(container, height) {

  if (!container.contains('line')) {
   container.append('line')
     .attr({
       'x1': 50,
       'y1': 0,
       'x2': 50,
       'y2': height
     })
     .attr("stroke", "#002900")
     .attr("class", "verticalLine");

   container.on('mousemove', function () {
     var xPosition = d3.mouse(this)[0];
     d3.selectAll(".verticalLine")
       .attr({
         "x1" : xPosition,
         "x2" : xPosition
       })
   });
  } else {
  // reset height
  container.find('line').attr({
                               'y2': height
                             })
  }
}

// calulates reads track height based on viewing range
function getTrackHeight() {
  var range = viewRegEnd - viewRegStart;
  var baseHeight = 14;

  if (range <= 1000)  {
    return baseHeight;
  } else if (range > 1000 && range < 10000)  {
    return baseHeight - 7;
  } else {
    return 4;
  }
}