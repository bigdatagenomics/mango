/**
 * Handles javascript for visualizing chromosome clickable wheel for navigation
**/

function refVis(dictionary, browser, fromOverall) {
  // Creating reference visualization from sequence dictionary
  var totalLength=0;

  // sum up total dictionary length
  for (i = 0; i < dictionary.length; i++) {
   totalLength+=dictionary[i].length;
  }

  var innerWidth = 45;
  var width = 180;
  var height = 180;
  var radius = Math.min(width, height) / 2;
  var color = d3.scale.category20b();
  var svg = d3.select('#refVis')
    .append('svg')
    .attr('width', width)
    .attr('height', height)
    .append('g')
    .attr('transform', 'translate(' + (width / 2) +
      ',' + (height / 2) + ')');
  var arc = d3.svg.arc()
    .innerRadius(radius-innerWidth)
    .outerRadius(radius);
  var pie = d3.layout.pie()
    .value(function(d) { return d.length/totalLength*100; }) //Express as percentage
    .sort(null);
  var path = svg.selectAll('path')
    .data(pie(dictionary))
    .enter()
    .append('path')
    .attr('d', arc)
    .attr('fill', function(d, i) {
      return color(d.data.name);
    });
  var tooltip = d3.select('#refVis')
  .append('div')
  .attr('class', 'refVistooltip');

  tooltip.append('div')
    .attr('class', 'name');

  tooltip.append('div')
    .attr('class', 'length');

   tooltip.append('div')
    .attr('class', 'percent');

  path.on('mouseover', function(d) {
    var total = d3.sum(dictionary.map(function(d) {
      return d.length;
    }));
    var percent = Math.round(1000 * d.data.length / total) / 10; //force 1 s.f.
    tooltip.select('.name').html(d.data.name);
    tooltip.select('.length').html(d.data.length);
    tooltip.select('.percent').html(percent + '%');
    tooltip.style('display', 'block');
  });

  path.on('click', function(d) {
    var start = Math.round(d.data.length/2.);
    var end =  Math.round(d.data.length/2. +1000);
    if (fromOverall){
        var request = '/setContig/' + d.data.name + '?start=' + start + '&end=' + end;
        var xhr = new XMLHttpRequest();
        xhr.open('GET', request, true);
        xhr.send();
        xhr.onreadystatechange = function() {
          if (xhr.readyState == 4 && xhr.status == 200) {
            window.location = '/browser';
          }
        }
    }
    // input range in control intput
    $(".controls>input").val(start + "-" + end);
    // select correct chromosome
    $("#list").val(d.data.name).trigger('change');

    // trigger event from updating controls for pileup browser
    browser.setRange({contig: d.data.name, start: start, stop: end});
  });

  path.on('mouseout', function() {
    tooltip.style('display', 'none');
  });

}
