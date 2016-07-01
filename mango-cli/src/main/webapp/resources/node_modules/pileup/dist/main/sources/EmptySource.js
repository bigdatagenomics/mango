/*
 * This is a dummy data source to be used by tracks that do not depend on data.
 * 
 */
'use strict';








var create = function create() {return { 
    rangeChanged: function rangeChanged() {}, 
    on: function on() {}, 
    off: function off() {}, 
    trigger: function trigger() {} };};


module.exports = { 
  create: create };