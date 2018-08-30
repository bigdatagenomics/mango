/* @flow */

// $FlowFixMe
var widgets = require('@jupyter-widgets/base');

var _ = require('underscore');
var pileup = require('pileup');
var utils = require("./utils");

var PileupModel = widgets.DOMWidgetModel.extend({
    defaults: _.extend(widgets.DOMWidgetModel.prototype.defaults(), {
        _model_name : 'PileupModel',
        _view_name : 'PileupView',
        _model_module : 'pileup',
        _view_module : 'pileup',
        _model_module_version : '0.1.0',
        _view_module_version : '0.1.0',
        locus : 'chr1:1-50',
        reference: 'hg19',
        tracks: []
    })
});


// Custom View. Renders the widget model.
var PileupView = widgets.DOMWidgetView.extend({
    render: function() {
        this.json_changed();
        this.model.on('change:tracks', this.data_changed, this);
        this.model.on('change:reference', this.data_changed, this);
    },

    data_changed: function() {

    // TODO map tracks to sources
    //var tracks = process_tracks(this.model.get('tracks'))
    console.log(this.model.get('tracks'))
    console.log(this.model.get('reference'))
    console.log(this.model.get('locus'))

      // make pileup div
      var sources = [
          {
            viz: pileup.viz.genome(),
            isReference: true,
            data: pileup.formats.twoBit({
              url: utils.genomeBuilds['hg19']
            }),
            name: 'Reference'
          },
          {
            viz: pileup.viz.scale(),
            name: 'Scale'
          }
      ];
      var contig = 'chr1'//this.model.get('locus').split(':')[0]
      var start =  100//this.model.get('locus').split(':')[1].split('-')[0]
      var stop =  200//this.model.get('locus').split(':')[1].split('-')[1]
      var range = {contig: contig, start: start, stop: stop};

      var p = pileup.create(this.el, {
        range: range,
        tracks: sources
      });
    }

    process_tracks: function(tracks) {
      // TODO process tracks to be readeable by pileup
      return tracks
    }
});


module.exports = {
    PileupModel : PileupModel,
    PileupView : PileupView
};
