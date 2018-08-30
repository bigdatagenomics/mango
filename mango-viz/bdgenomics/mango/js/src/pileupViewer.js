/* @flow */

// $FlowFixMe
var widgets = require('@jupyter-widgets/base');

var _ = require('underscore');
var pileup = require('pileup');
var utils = require("./utils");

var PileupViewerModel = widgets.DOMWidgetModel.extend({
    defaults: _.extend(widgets.DOMWidgetModel.prototype.defaults(), {
        _model_name : 'PileupViewerModel',
        _view_name : 'PileupViewerView',
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
var PileupViewerView = widgets.DOMWidgetView.extend({
    render: function() {
        this.data_changed();
        this.model.on('change:tracks', this.data_changed, this);
        this.model.on('change:reference', this.data_changed, this);
    },

    data_changed: function() {

      console.log(this.model.get('tracks'));
      console.log(this.model.get('reference'));
      console.log(this.model.get('locus'));

      // make pileup div
      var sources = [
          {
            viz: pileup.viz.genome(),
            isReference: true,
            data: pileup.formats.twoBit({
              url: utils.genomeBuilds[this.model.get('reference')]
            }),
            name: 'Reference'
          },
          {
            viz: pileup.viz.scale(),
            name: 'Scale'
          }
      ];

      // add in optional tracks
      for (var i = 0; i < this.model.get('tracks').length; i++) {
        var track = this.model.get('tracks')[i]

        sources.push({
          viz: pileup.viz[track.viz](),
          data: pileup.formats[track.format](track.sourceOptions),
          name: track.label
        })
      }

      console.log(sources);

      var contig = this.model.get('locus').split(':')[0];
      var start =  parseInt(this.model.get('locus').split(':')[1].split('-')[0]);
      var stop =  parseInt(this.model.get('locus').split(':')[1].split('-')[1]);
      var range = {contig: contig, start: start, stop: stop};

      var p = pileup.create(this.el, {
        range: range,
        tracks: sources
      });
    }
});


module.exports = {
    PileupViewerModel : PileupViewerModel,
    PileupViewerView : PileupViewerView
};
