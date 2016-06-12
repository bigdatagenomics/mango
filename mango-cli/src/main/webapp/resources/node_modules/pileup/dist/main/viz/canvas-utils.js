/**
 * Utility code for working with the HTML canvas element.
 *
 * 
 */
'use strict';

// Return the 2D context for a canvas. This is helpful for type safety.
function getContext(el) {
  // The typecasts through `any` are to fool flow.
  var canvas = el;
  var ctx = canvas.getContext('2d');
  return ctx;}


// Stroke a line between two points
function drawLine(ctx, x1, y1, x2, y2) {
  ctx.beginPath();
  ctx.moveTo(x1, y1);
  ctx.lineTo(x2, y2);
  ctx.stroke();}


module.exports = { 
  getContext: getContext, 
  drawLine: drawLine };