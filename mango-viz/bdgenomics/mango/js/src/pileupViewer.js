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
    pileup: null,

    render: function() {
      this.data_changed();
      this.listenTo(this.model, 'change:locus', this._locus_changed, this);
      this.listenTo(this.model, 'change:msg', this._msg_changed, this);
    },

    _locus_changed: function() {
        // TODO share code (copied from below)
        var contig = this.model.get('locus').split(':')[0];
        var start =  parseInt(this.model.get('locus').split(':')[1].split('-')[0]);
        var stop =  parseInt(this.model.get('locus').split(':')[1].split('-')[1]);
        var range = {contig: contig, start: start, stop: stop};

        this.pileup.setRange(range);
    },

    _msg_changed: function() {

        switch(this.model.get('msg')) {
            case 'zoomIn':
                console.log("zooming in");
                this.pileup.zoomIn();
                // TODO propogate back locus
                console.log(this.pileup.getRange());
                this.model.set('locus', this.pileup.getRange());
                break;

            case 'zoomOut':
                console.log("zooming out");
                this.pileup.zoomOut();
                // TODO propogate back locus
                console.log(this.pileup.getRange());
                this.model.set('locus',this.pileup.getRange());
                break;

            case 'toSVG':
                console.log("toSVG");
                this.pileup.getSVG().then(svg => {
                    console.log(svg);
                    // TODO: get back to python
                    this.model.set('svg',svg);
                    break;
                });
        }
    },

    data_changed: function() {
        alert("data changed");

      // listen for errors so we can bubble them up to the Jupyter interface.
      // TODO: this would ideally be embedded in the widget
      window.onerror = function errorHandler(errorMsg, url, lineNumber) {
          var errText = `Javascript error occured at ${url}:${lineNumber} \n ${errorMsg}`;
          alert(errText);
      }

      // reference URL can be a name (ie hg19, valid names
      // are specified in utils.js) or a URL to a 2bit file.
      var referenceUrl = utils.genomeBuilds[this.model.get('reference')];

      // if reference name is not found in genomeBuilds dictionary,
      // it should just be a URL
      if (referenceUrl == null || referenceUrl == undefined) {
        referenceUrl = this.model.get('reference');
      }

      var referenceTrack = {
            viz: pileup.viz.genome(),
            isReference: true,
            data: pileup.formats.twoBit({
              url: referenceUrl
            }),
            name: 'Reference'
      };

      // make list of pileup sources
      var sources = [referenceTrack];

      // add in optional tracks
      for (var i = 0; i < this.model.get('tracks').length; i++) {
        var track = this.model.get('tracks')[i]

        var newTrack = {
          viz: pileup.viz[track.viz](),
          name: track.label
        };

        // data may not exist for scale or location tracks
        if (pileup.formats[track.source] != null) {
          newTrack.data = pileup.formats[track.source](track.sourceOptions);
        }
        sources.push(newTrack);
      }

      var contig = this.model.get('locus').split(':')[0];
      var start =  parseInt(this.model.get('locus').split(':')[1].split('-')[0]);
      var stop =  parseInt(this.model.get('locus').split(':')[1].split('-')[1]);
      var range = {contig: contig, start: start, stop: stop};

      this.pileup = pileup.create(this.el, {
        range: range,
        tracks: sources
      });

    }
});


module.exports = {
    PileupViewerModel : PileupViewerModel,
    PileupViewerView : PileupViewerView
};
