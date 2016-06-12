(function() {

'use strict';

var expect = chai.expect;

describe('data-canvas', function() {
  var testDiv = document.getElementById('testdiv');
  var canvas;

  before(function() {
    canvas = document.createElement('canvas');
    canvas.width = 600;
    canvas.height = 200;
    testDiv.appendChild(canvas);
  });

  after(function() {
    // testDiv.innerHTML = '';  // avoid pollution between tests.
  });

  function rgbAtPos(im, x, y) {
    var index = y * (im.width * 4) + x * 4;
    return [
      im.data[index],
      im.data[index + 1],
      im.data[index + 2]
    ];
  }

  describe('DataContext', function() {
    it('should put pixels on the canvas', function() {
      if (!canvas) throw 'bad';  // for flow
      var ctx = canvas.getContext('2d');
      var dtx = dataCanvas.getDataContext(ctx);

      dtx.fillStyle = 'red';
      dtx.fillRect(100, 50, 200, 25);
      dtx.pushObject({something: 'or other'});
      dtx.popObject();

      var im = ctx.getImageData(0, 0, 600, 400);
      expect(rgbAtPos(im, 50, 50)).to.deep.equal([0, 0, 0]);
      expect(rgbAtPos(im, 200, 60)).to.deep.equal([255, 0, 0]);
    });

    it('should cache calls', function() {
      if (!canvas) throw 'bad';  // for flow
      var ctx = canvas.getContext('2d');
      var dtx = dataCanvas.getDataContext(canvas);
      var dtx2 = dataCanvas.getDataContext(ctx);

      expect(dtx2).to.equal(dtx);
    });

    it('should support read/write to properties', function() {
      var dtx = dataCanvas.getDataContext(canvas);
      dtx.lineWidth = 10;
      expect(dtx.lineWidth).to.equal(10);
    });
  });

  describe('ClickTrackingContext', function() {
    var ctx;
    before(function() {
      if (!canvas) throw 'bad';  // for flow
      ctx = canvas.getContext('2d');
      ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
    });

    function getObjectsAt(draw, x, y) {
      var dtx = new dataCanvas.ClickTrackingContext(ctx, x, y);
      draw(dtx);
      return dtx.hits;
    }

    // To draw any of these:
    // draw(dataCanvas.getDataContext(ctx));

    it('should track clicks on rects', function() {
      function draw(dtx) {
        dtx.pushObject('r');
        dtx.fillStyle = 'red';
        dtx.fillRect(100, 50, 100, 25);
        dtx.popObject();
        dtx.pushObject('b');
        dtx.fillStyle = 'blue';
        dtx.fillRect(300, 100, 200, 25);
        dtx.popObject();
      }

      expect(getObjectsAt(draw, 150, 60)).to.deep.equal([['r']]);
      expect(getObjectsAt(draw, 350, 110)).to.deep.equal([['b']]);
      expect(getObjectsAt(draw, 250, 110)).to.deep.equal([]);
    });

    it('should track clicks on complex shapes', function() {
      function draw(dtx) {
        // This is the upper right half of a rectangle, i.e. a triangle.
        dtx.pushObject('triangle');
        dtx.beginPath();
        dtx.moveTo(100, 100);
        dtx.lineTo(400, 100);
        dtx.lineTo(400, 200);
        dtx.closePath();
        dtx.fill();
        dtx.popObject();
      }

      // This point is in the top right (and hence in the triangle)
      expect(getObjectsAt(draw, 300, 110)).to.deep.equal([['triangle']]);
      // This poitn is in the bottom left (and hence not in the triangle)
      expect(getObjectsAt(draw, 200, 180)).to.deep.equal([]);
    });

    it('should track clicks on stacked shapes', function() {
      function draw(dtx) {
        dtx.pushObject('bottom');
        dtx.fillStyle = 'red';
        dtx.fillRect(100, 50, 400, 100);
        dtx.pushObject('top');
        dtx.fillStyle = 'blue';
        dtx.fillRect(200, 75, 100, 50);
        dtx.popObject();
        dtx.popObject();
        dtx.pushObject('side');
        dtx.fillStyle = 'green';
        dtx.fillRect(450, 75, 100, 50);
        dtx.popObject();
      }

      draw(dataCanvas.getDataContext(ctx));
      expect(getObjectsAt(draw, 110, 60)).to.deep.equal([['bottom']]);
      expect(getObjectsAt(draw, 250, 100)).to.deep.equal([['top', 'bottom'], ['bottom']]);
      expect(getObjectsAt(draw, 475, 100)).to.deep.equal([['side'], ['bottom']]);
    });

    it('should reset hit tracker', function() {
      function draw(dtx) {
        dtx.reset();
        dtx.clearRect(0, 0, dtx.canvas.width, dtx.canvas.height);
        dtx.pushObject('rect');
        dtx.fillRect(100, 10, 200, 30);
        dtx.popObject();
      }
      function doubledraw(dtx) {
        draw(dtx);
        draw(dtx);
      }

      // Despite the double-drawing, only one object matches, not two.
      // This is because of the reset() call.
      doubledraw(dataCanvas.getDataContext(ctx));
      expect(getObjectsAt(doubledraw, 110, 30)).to.deep.equal([['rect']]);
    });

    // PhantomJS 1.9.x does not support isStrokeInPath
    // When Travis-CI updates to Phantom2, this can be re-enabled.
    // See https://github.com/ariya/phantomjs/issues/12948
    if (!navigator.userAgent.match(/PhantomJS\/1.9/)) {
      it('should detect clicks in strokes', function() {
        function draw(dtx) {
          dtx.save();
          dtx.pushObject('shape');
          dtx.lineWidth = 5;
          dtx.beginPath();
          dtx.moveTo(100, 10);
          dtx.lineTo(200, 10);
          dtx.lineTo(200, 30);
          dtx.lineTo(100, 30);
          dtx.closePath();
          dtx.stroke();
          dtx.popObject();
          dtx.restore();
        }

        draw(dataCanvas.getDataContext(ctx));
        // a click on the stroke is a hit...
        expect(getObjectsAt(draw, 100, 10)).to.deep.equal([['shape']]);
        // ... while a click in the interior is not.
        expect(getObjectsAt(draw, 150, 20)).to.deep.equal([]);
      });
    }

  });

  describe('RecordingContext', function() {
    var RecordingContext = dataCanvas.RecordingContext;

    var ctx;
    before(function() {
      if (!canvas) throw 'bad';  // for flow
      ctx = canvas.getContext('2d');
      ctx.clearRect(0, 0, ctx.canvas.width, ctx.canvas.height);
    });

    describe('single canvas', function() {
      it('should record calls', function() {
        var dtx = new RecordingContext(ctx);
        dtx.fillStyle = 'red';
        dtx.pushObject('a');
        dtx.fillRect(100, 50, 200, 25);
        dtx.popObject();

        expect(dtx.calls).to.have.length(3); // push, fill, pop
        expect(dtx.drawnObjectsWith(function(x) { return x == 'a' })).to.have.length(1);
        expect(dtx.drawnObjectsWith(function(x) { return x == 'b' })).to.have.length(0);

        // TODO: check drawing styles
      });

      it('should return values from proxied functions', function() {
        var dtx = new RecordingContext(ctx);
        var metrics = dtx.measureText('Hello');

        expect(dtx.calls).to.deep.equal([['measureText', 'Hello']]);
        expect(metrics.width).to.be.greaterThan(0);
      });

      it('should provid static testing methods', function() {
        RecordingContext.recordAll();
        var dtx = dataCanvas.getDataContext(ctx);
        dtx.pushObject('hello');
        dtx.fillText('hello', 100, 10);
        dtx.popObject();

        expect(RecordingContext.drawnObjects()).to.deep.equal(['hello']);
        expect(RecordingContext.drawnObjectsWith(function(x) { return x == 'hello' })).to.deep.equal(['hello']);
        expect(RecordingContext.callsOf('fillText')).to.deep.equal(
            [['fillText', 'hello', 100, 10]]);

        RecordingContext.reset();
      });

      it('should reset the list of calls', function() {
        function render(dtx) {
          dtx.reset();  // this clears the list of calls
          dtx.pushObject('hello');
          dtx.fillText('hello', 100, 10);
          dtx.popObject();
        }

        RecordingContext.recordAll();
        var dtx = dataCanvas.getDataContext(ctx);
        render(dtx);
        render(dtx);

        // Only one object, not two (even though there are two render calls).
        expect(RecordingContext.drawnObjects()).to.have.length(1);

        RecordingContext.reset();
      });
    });

    describe('multiple canvases', function() {
      var canvas2;
      before(function() {
        canvas2 = document.createElement('canvas');
        canvas2.width = 400;
        canvas2.height = 100;
        canvas2.setAttribute('class', 'canvas2');
        canvas.setAttribute('class', 'canvas1');
        testDiv.appendChild(canvas2);
      });

      it('should record calls to both canvases', function() {
        function render(dtx, text) {
          dtx.pushObject(text);
          dtx.fillText(text, 100, 10);
          dtx.popObject();
        }

        RecordingContext.recordAll();

        var dtx1 = dataCanvas.getDataContext(canvas),
            dtx2 = dataCanvas.getDataContext(canvas2);
        render(dtx1, 'Hello #1');
        render(dtx2, 'Hello #2');

        expect(function() {
          RecordingContext.drawnObjects();
        }).to.throw(/multiple canvases/);

        expect(RecordingContext.drawnObjects(testdiv, '.canvas1'))
            .to.deep.equal(['Hello #1']);
        expect(RecordingContext.drawnObjects(testdiv, '.canvas2'))
            .to.deep.equal(['Hello #2']);

        expect(RecordingContext.callsOf(testdiv, '.canvas1', 'fillText'))
            .to.deep.equal([['fillText', 'Hello #1', 100, 10]]);
        expect(RecordingContext.callsOf(testdiv, '.canvas2', 'fillText'))
            .to.deep.equal([['fillText', 'Hello #2', 100, 10]]);

        expect(function() {
          RecordingContext.drawnObjects(testdiv, '.canvas3');
        }).to.throw(/Unable to find.*\.canvas3/);

        RecordingContext.reset();
      });

      it('should throw on matching non-canvas', function() {
        testDiv.innerHTML += '<div class=foo>Foo</div>';
        RecordingContext.recordAll();
        expect(function() {
          RecordingContext.drawnObjects(testdiv, '.foo');
        }).to.throw(/.foo neither matches nor contains/);
        RecordingContext.reset();
      });

      it('should throw before recording', function() {
        // TODO: this error message doesn't make much sense for a user.
        expect(function() {
          RecordingContext.drawnObjects(testdiv, '.canvas1');
        }).to.throw(/must call .*recordAll.*other.*static methods/);
      });
    });

    describe('error cases', function() {
      it('should throw on reset before record', function() {
        expect(function() {
          RecordingContext.reset();
        }).to.throw(/reset.*before.*recordAll/);
      });

      it('should throw on double record', function() {
        expect(function() {
          RecordingContext.recordAll();
          RecordingContext.recordAll();
        }).to.throw(/forgot.*reset/);
        RecordingContext.reset();
      });

      it('should throw on access without recording', function() {
        expect(function() {
          RecordingContext.drawnObjects();
        }).to.throw(/You must call .*recordAll/);
      });

      it('should throw on access with nothing recorded', function() {
        expect(function() {
          RecordingContext.recordAll();
          RecordingContext.drawnObjects();
        }).to.throw(/no canvases are being recorded/);
        RecordingContext.reset();
      });
    });

    describe('drawImage', function() {
      beforeEach(function() {
        RecordingContext.recordAll();
      });

      afterEach(function() {
        RecordingContext.reset();
      });

      function makeOffscreenImage() {
        var image = document.createElement('canvas');
        image.width = 100;
        image.height = 100;
        var dtx = dataCanvas.getDataContext(image);
        dtx.pushObject('A');
        dtx.fillRect(0, 0, 50, 50);
        dtx.popObject();
        return image;
      }

      it('should transfer recorded calls', function() {
        var image = makeOffscreenImage();
        var dtx = dataCanvas.getDataContext(canvas);
        dtx.drawImage(image, 0, 0);

        expect(dtx.calls).to.have.length(3);
        expect(dtx.drawnObjects()).to.deep.equal(['A']);
        expect(dtx.callsOf('fillRect')).to.deep.equal([['fillRect', 0, 0, 50, 50]]);
        // The drawImage call is elided.
        // This could be changed -- either way would be reasonable.
        expect(dtx.callsOf('drawImage')).to.deep.equal([]);
      });

      it('should translate recorded calls', function() {
        var image = makeOffscreenImage();
        var dtx = dataCanvas.getDataContext(canvas);
        dtx.drawImage(image, 50, 0);  // dx=50

        expect(dtx.calls).to.have.length(3);
        expect(dtx.callsOf('fillRect')).to.deep.equal([['fillRect', 50, 0, 50, 50]]);
      });

      it('should transform recorded calls', function() {
        var image = makeOffscreenImage();
        var dtx = dataCanvas.getDataContext(canvas);
        dtx.drawImage(image, 50, 0, 75, 50);  // dx=50, dWidth=75, dHeight=50

        expect(dtx.calls).to.have.length(3);
        expect(dtx.callsOf('fillRect')).to.deep.equal([['fillRect', 50, 0, 37.5, 25]]);
      });

      it('should support a source rectangle', function() {
        var image = makeOffscreenImage();
        var dtx = dataCanvas.getDataContext(canvas);
        // This copies x=75-100 and y=50-100 from source to dest
        dtx.drawImage(image, 25, 50, 75, 50, 0, 0, 75, 50);

        expect(dtx.calls).to.have.length(3);
        expect(dtx.callsOf('fillRect')).to.deep.equal([['fillRect', -25, -50, 50, 50]]);
      });

      it('should reject invalid drawImage calls', function() {
        var image = makeOffscreenImage();
        var dtx = dataCanvas.getDataContext(canvas);
        expect(function() {
          dtx.drawImage(image, 50, 0, 75);  // four params, should be 3, 5 or 9
        }).to.throw();  // exact error depends on browser
      });

      it('should transform paths', function() {
        var image = makeOffscreenImage();
        var ctx = dataCanvas.getDataContext(image);
        ctx.beginPath();
        ctx.moveTo(20, 10);
        ctx.lineTo(30, 20);
        ctx.quadraticCurveTo(50, 20, 40, 30);
        ctx.closePath();

        var dtx = dataCanvas.getDataContext(canvas);
        dtx.drawImage(image, 0, 10, 50, 25);  // dx=0, dy=10, dWidth=50, dHeight=25
        expect(dtx.callsOf('moveTo')).to.deep.equal([['moveTo', 10, 12.5]]);
        expect(dtx.callsOf('lineTo')).to.deep.equal([['lineTo', 15, 15]]);
        expect(dtx.callsOf('quadraticCurveTo')).to.deep.equal(
            [['quadraticCurveTo', 25, 15, 20, 17.5]]);
      });

      it('should not transfer calls from unrecorded canvases', function() {
        var image = document.createElement('canvas');
        image.width = 100;
        image.height = 100;
        image.getContext('2d').fillRect(0, 0, 100, 100);
        var dtx = dataCanvas.getDataContext(canvas);
        dtx.drawImage(image, 0, 0);

        // The fillRect call should not be transferred over.
        expect(dtx.callsOf('drawImage')).to.deep.equal(
            [['drawImage', image, 0, 0]]);
        expect(dtx.callsOf('fillRect')).to.deep.equal([]);
      });

      // Regression test for #13
      it('should record calls after drawImage', function() {
        var image = makeOffscreenImage();
        var dtx = dataCanvas.getDataContext(canvas);
        dtx.clearRect(0, 0, 200, 50);
        dtx.drawImage(image, 0, 0);
        dtx.fillRect(20, 10, 100, 40);

        expect(dtx.calls).to.have.length(5);
        expect(dtx.drawnObjects()).to.deep.equal(['A']);
        expect(dtx.callsOf('clearRect')).to.deep.equal([['clearRect', 0, 0, 200, 50]]);
        expect(dtx.callsOf('fillRect')).to.deep.equal([
            ['fillRect', 0, 0, 50, 50],
            ['fillRect', 20, 10, 100, 40]
        ]);
      });
    });
  });
});

})();
