/**
 * Primitives to help with async testing.
 * 
 */
'use strict';function _interopRequireDefault(obj) {return obj && obj.__esModule ? obj : { 'default': obj };}var _q = require(

'q');var _q2 = _interopRequireDefault(_q);

var WAIT_FOR_POLL_INTERVAL_MS = 100;

// Returns a promise which resolves when predFn() is truthy.
function waitFor(predFn, timeoutMs) {
  var def = _q2['default'].defer();

  var checkTimeoutId = null;

  var timeoutId = window.setTimeout(function () {
    if (checkTimeoutId) window.clearTimeout(checkTimeoutId);
    def.reject('Timed out');}, 
  timeoutMs);

  var check = function check() {
    if (def.promise.isRejected()) return;
    if (predFn()) {
      def.resolve(null); // no arguments needed
      window.clearTimeout(timeoutId);} else 
    {
      checkTimeoutId = window.setTimeout(check, WAIT_FOR_POLL_INTERVAL_MS);}};


  checkTimeoutId = window.setTimeout(check, 0);

  return def.promise;}



module.exports = { 
  waitFor: waitFor };