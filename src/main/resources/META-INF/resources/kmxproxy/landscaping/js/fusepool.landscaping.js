/* JSlint hints: */
/*globals _,$ */
var _, $, THREE, FusePool;

/**
 * FusePool namespace.
 *
 * This is the global namespace under which we place the Landscaping part. If
 * FusePool is not yet defined (or otherwise evaluated to false) we will make
 * a new empty object.
 *
 * @type {*|{}}
 * @author: Daniël van Adrichem <daniel@treparel.com>
 */
FusePool = window["FusePool"] || {};

/**
 * FusePool.Landscaping namespace.
 *
 * It is the API to and contains all code regarding the client side landscaping
 * visualization support.
 *
 * It is assumed that jQuery is available when this script is included.
 *
 * @type {{}}
 * @author: Daniël van Adrichem <daniel@treparel.com>
 */
FusePool.Landscaping = {
    isPanning: false,
    lastPanX: 0,
    lastPanY: 0,
    MAX_PICK_DISTANCE: 0.005,
    baseURL: 'kmxproxy/landscaping/',
    /// When set a redraw occurs next timer event
    update: true,
    debugRenderParticlesOnly: false,
    debugFullBlast: true, // if set ignore this.update
    /**
     * Initialize the Landscape component.
     *
     * The node found using the given selector will be the container where
     * the landscape will be added to.
     *
     * @param selector
     * @author: Daniël van Adrichem <daniel@treparel.com>
     */
    initialize: function (selector, enyoApp) {
        var thisLandscaping = this;
        thisLandscaping.enyoApp = enyoApp;

        // for debug purposes
//        $("#landscape-render-button").on("click", function () {
//            thisLandscaping.renderAll();
//        });

        thisLandscaping.$container = $(selector);
        thisLandscaping.$container.empty();
        thisLandscaping.$container.append("<p>Please execute a search.</p>");
//        var b = $('<button>');
//        b.addClass('onyx-button');
//        b.text('Request landscape');
//        thisLandscaping.$container.append(b);
//        b.on('click', function () {
//            FusePool.Landscaping.doSearch();
//        });
    }
};

FusePool.Landscaping.doSearch = function () {
    var thisLandscaping = this;
    thisLandscaping.$container.empty();
//    jsondata is mock data found in jsondata.js
//    thisLandscaping.loadData(jsondata);
    FusePool.Landscaping.enyoApp.$.loader.show();
    FusePool.Landscaping.requestLandscape(function(data) {
        thisLandscaping.loadData(data);
//        thisLandscaping.renderAll();
        thisLandscaping.update = true;
        FusePool.Landscaping.enyoApp.$.loader.hide();
    });
};

FusePool.Landscaping.requestLandscape = function(onDataRecieved) {
    var thisLandscaping = this;
    var o = FusePool.Landscaping.enyoApp;
    var postParams = {};

    // Set the content store properties
    postParams.contentStoreUri = CONSTANTS.SEARCH_URL;
    postParams.contentStoreViewUri = CONSTANTS.SEARCH_URL;

    // Set the search properties
    postParams.searchs = [];
    postParams.searchs.push(o.owner.searchWord);

    // Set the checked facets and type facets
    postParams.type = [];
    postParams.subject = [];
    for(var i=0;i<o.owner.checkedEntities.length;i++){
        if(o.owner.checkedEntities[i].typeFacet){
            postParams.type.push(o.owner.checkedEntities[i].id);
        } else {
            postParams.subject.push(o.owner.checkedEntities[i].id);
        }
    }
    console.debug(postParams);
    
    var url = CONSTANTS.LANDSCAPE_URL;
    var postBody = JSON.stringify(postParams);
    $.ajax({
        type: 'POST',
        url: url,
        data: postBody,
        contentType: "application/json; charset=utf-8",
        dataType: "json",
        success: function(data){
            onDataRecieved(data);
            window.requestAnimationFrame(FusePool.Landscaping.renderAll);

//            thisLandscaping.renderAll();
        },
        error: function() {
            thisLandscaping.$container.empty();
            thisLandscaping.$container.append("<p>No Data Recieved.</p>");
        }
    });
};

/**
 * Load json data and init and show the landscape
 * 
 * @param {type} data
 * @returns {undefined}
 */
FusePool.Landscaping.loadData = function (data) {
        var thisLandscaping = this;
        
        console.info("Initializing FusePool.Landscaping: " + thisLandscaping.$container);
        var canvasElement = thisLandscaping.initThree(
            thisLandscaping.$container.width(),
            thisLandscaping.$container.height(),
            data
        );
        thisLandscaping.$canvas = $(canvasElement);
        thisLandscaping.$container.append(canvasElement);

        thisLandscaping.mouseVec2 = new THREE.Vector2();

        thisLandscaping.initMouseHandlers();

        console.info("Initializing FusePool.Landscaping Done (" +
            thisLandscaping.$canvas.width() + "x" + thisLandscaping.$canvas.height() + ")");

};

/**
 * React to change of focus.
 *
 * @param {type} document
 * @returns {undefined}
 * @author: Daniël van Adrichem <daniel@treparel.com>
 */
FusePool.Landscaping.doFocusDocument = function (document) {
    console.debug("picked", document);
    FusePool.Landscaping.enyoApp.owner.openDoc(document.ID);
};

/**
 * Returns a scale factor that can be used to determine the amount of panning movement, given
 * normalized (-1..1) mouse coords.
 * 
 * @returns {Number}
 * @author: Daniël van Adrichem <daniel@treparel.com>
*/
FusePool.Landscaping.cameraScale = function () {
    var width = this.camera.right - this.camera.left;
    var height = this.camera.top - this.camera.bottom;
    
    // we want the ratio between current dimentions and 2 (since the mouse coords are normalized
    // to -1..1)
    // also, for the time being, lets assume uniform scaling (ie. height === width)
    return width / 2;
};

/**
 * Set panning state
 * 
 * @param {type} vec
 * @returns {undefined}
 * @author: Daniël van Adrichem <daniel@treparel.com>
 */
FusePool.Landscaping.startPanning = function (vec) {
    this.isPanning = true;
    this.lastPanX = vec.x;
    this.lastPanY = vec.y;
//    FusePool.Landscaping.updatePanning(vec);
};

/**
 * Unset panning state
 * 
 * @param {type} vec
 * @returns {undefined}
 * @author: Daniël van Adrichem <daniel@treparel.com>
 */
FusePool.Landscaping.stopPanning = function (vec) {
    this.isPanning = false;
//    this.lastPanX = vec.x;
//    this.lastPanY = vec.y;
};

/**
 * Update panning after mouse movement
 * 
 * @param {type} vec
 * @returns {undefined}
 * @author: Daniël van Adrichem <daniel@treparel.com>
 */
FusePool.Landscaping.updatePanning = function (vec) {
    if (this.isPanning === true) {
        // get delta
        var dx = vec.x - this.lastPanX;
        var dy = vec.y - this.lastPanY;

        var scale = FusePool.Landscaping.cameraScale();
        this.camera.position.x -= dx * scale;
        this.camera.position.y -= dy * scale;
        this.update = true;
//        this.renderAll();
    }
    this.lastPanX = vec.x;
    this.lastPanY = vec.y;
};

/**
 * Initialize mouse event handlers
 *
 * @author: Daniël van Adrichem <daniel@treparel.com>
 */
FusePool.Landscaping.initMouseHandlers = function () {
    var thisLandscaping = this;
    this.$canvas.on("mousemove", function (event) {
        event.preventDefault();
        event.stopPropagation();
        var vec = new THREE.Vector3(
                (event.offsetX / thisLandscaping.$canvas.width()) * 2.0 - 1,
                -(event.offsetY / thisLandscaping.$canvas.height()) * 2.0 + 1);
        FusePool.Landscaping.updatePanning(vec);
    });
    this.$canvas.on("mouseleave", function (event) {
        event.preventDefault();
        event.stopPropagation();
        var vec = new THREE.Vector3(
                (event.offsetX / thisLandscaping.$canvas.width()) * 2.0 - 1,
                -(event.offsetY / thisLandscaping.$canvas.height()) * 2.0 + 1);
        FusePool.Landscaping.stopPanning(vec);
    });
    this.$canvas.on("mouseup", function (event) {
        event.preventDefault();
        event.stopPropagation();
        var vec = new THREE.Vector3(
                (event.offsetX / thisLandscaping.$canvas.width()) * 2.0 - 1,
                -(event.offsetY / thisLandscaping.$canvas.height()) * 2.0 + 1);
        FusePool.Landscaping.stopPanning(vec);
    });
    this.$canvas.on("mousedown", function (event) {
        event.preventDefault();
        event.stopPropagation();
        // mouseVec2 contains the screenspace coordinate of the mouse, with origin top left and
        // position normalized to -1..1
        thisLandscaping.mouseVec2.x = (event.offsetX / thisLandscaping.$canvas.width()) * 2.0 - 1;
        thisLandscaping.mouseVec2.y = -(event.offsetY / thisLandscaping.$canvas.height()) * 2.0 + 1;
        
        // create vec3 from the mouse pos, with 1 for its z value
//        var vec3 = new THREE.Vector3(thisLandscaping.mouseVec2.x,
//            thisLandscaping.mouseVec2.y,  0.5);
        
        // unproject the mouse vector. Not very exciting since we use othographic projection
//        thisLandscaping.projector.unprojectVector(vec3, thisLandscaping.camera);
        
        // use raycasting to see if the mouse click hit an object
        // first set up the raycaster 
//        thisLandscaping.raycaster.set(thisLandscaping.camera.position,
//                vec3.sub(thisLandscaping.camera.position ).normalize());
                
       // lets roll our own Ray suitable for use with orthographic projections
        var vecO = new THREE.Vector3( thisLandscaping.mouseVec2.x, thisLandscaping.mouseVec2.y, - 1 );
        var vecT = new THREE.Vector3( thisLandscaping.mouseVec2.x, thisLandscaping.mouseVec2.y, 1 );
        var camera = thisLandscaping.camera;
        // unproject them indivually
        thisLandscaping.projector.unprojectVector( vecO, camera );
        thisLandscaping.projector.unprojectVector( vecT, camera );
        // picking ray direction
        vecT.sub( vecO ).normalize();
        // initialize raycaster
        var raycaster = new THREE.Raycaster(vecO, vecT);
        // let three's raycaster find the intersections
        var intersects = raycaster.intersectObjects([thisLandscaping.particleSystem]);

//        var intersects = thisLandscaping.raycaster.intersectObjects(
//                thisLandscaping.scene.children);
//
//        var intersect_index = FusePool.Landscaping.getIntersection(thisLandscaping.particleSystem,
//            thisLandscaping.raycaster.ray);
//        var intersect = thisLandscaping.datapoints[intersect_index];
//        thisLandscaping.particleSystem.geometry.attributes.position.array[intersect_index*3+0] = 0;
//        thisLandscaping.particleSystem.geometry.attributes.position.array[intersect_index*3+1] = 0;
//        thisLandscaping.particleSystem.geometry.attributes.position.array[intersect_index*3+2] = 0;
//        thisLandscaping.particleSystem.geometry.verticesNeedUpdate = true;
//        thisLandscaping.particleSystem.geometry.dynamic = true;
//        // TODO fire document focus callback
//        console.debug(intersect);
//        var intersects = thisLandscaping.raycaster.intersectObjects([thisLandscaping.particleSystem]);
        if (intersects.length !== 0) {
            var closest = intersects[0];
            // if close enough we focus this document
            if (closest.distance < thisLandscaping.MAX_PICK_DISTANCE) {
                FusePool.Landscaping.doFocusDocument(thisLandscaping.datapoints[closest.index]);
            }
            // if not so close we go into panning mode
            else {
                var vec = new THREE.Vector3(
                        (event.offsetX / thisLandscaping.$canvas.width()) * 2.0 - 1,
                        -(event.offsetY / thisLandscaping.$canvas.height()) * 2.0 + 1);
                FusePool.Landscaping.startPanning(vec);
            }
        }
        
        thisLandscaping.update = true;
    });
    this.$canvas.on("mousewheel", function(event) {
//        console.log(event.deltaX, event.deltaY, event.deltaFactor);
        // 0.05 will be 10% larger or smaller, because it applies to both left
        // and right and top and bottom. We flip the sign, so scrolling
        // away from oneself zooms out
        var zoomFactor = 1 + -event.deltaY * 0.05;
        thisLandscaping.camera.left *= zoomFactor;
        thisLandscaping.camera.right *= zoomFactor;
        thisLandscaping.camera.top *= zoomFactor;
        thisLandscaping.camera.bottom *= zoomFactor;
        thisLandscaping.camera.updateProjectionMatrix();
        thisLandscaping.update = true;
//        thisLandscaping.renderAll();
    });
};

FusePool.Landscaping.renderAll = function () {
    // only render when this bool is set
    if (FusePool.Landscaping.update || FusePool.Landscaping.debugFullBlast) {
//        console.info("Landscape redraw");
        // reset update bool
        FusePool.Landscaping.update = false;

        // update size of dots
        FusePool.Landscaping.dotMaterialConv.size = FusePool.Landscaping.radius;
        FusePool.Landscaping.particleSystem.material = FusePool.Landscaping.dotMaterialConv;
        FusePool.Landscaping.renderer.clear();

        if (FusePool.Landscaping.debugRenderParticlesOnly) {
            FusePool.Landscaping.renderer.render(FusePool.Landscaping.scene, FusePool.Landscaping.camera);
        } else {
            // firstly render the scene containing the dots particle system to
            // the FusePool.Landscaping.rtTexturePing render target and force clear (the true param)
            FusePool.Landscaping.renderer.render(FusePool.Landscaping.scene, FusePool.Landscaping.camera, FusePool.Landscaping.rtTexturePing, true);
            // next render the scene with a quad and shader having source rtTexturePing
            // FusePool.Landscaping applies horizontal convolution the output is rendered to
            // FusePool.Landscaping.rtTexturePong
            FusePool.Landscaping.renderer.render(FusePool.Landscaping.scenePing, FusePool.Landscaping.camera, FusePool.Landscaping.rtTexturePong, true);
            // finally render the scene consisting of a quad and its shader does
            // vertical convolution with rtTexturePong as source and no render
            // target, so output on canvas.
            FusePool.Landscaping.renderer.render(FusePool.Landscaping.scenePong, FusePool.Landscaping.camera);
            FusePool.Landscaping.particleSystem.material = FusePool.Landscaping.dotMaterial;
            FusePool.Landscaping.renderer.render(FusePool.Landscaping.scene, FusePool.Landscaping.camera);
        }
    }
    window.requestAnimationFrame(FusePool.Landscaping.renderAll);
};

/**
 * Initialize all web GL components using THREE.js.
 *
 * @param width
 * @param height
 * @author: Daniël van Adrichem <daniel@treparel.com>
 */
FusePool.Landscaping.initThree = function (width, height, data) {
    // we will be adding the canvas to $container. The size of this
    // container determines the size of the canvas.

    // the main scene
    this.scene = new THREE.Scene();
    // two scenes used to apply convolution fragment shader
    this.scenePing = new THREE.Scene();
    this.scenePong = new THREE.Scene();

    // the main view port
    // origin in center of $container, params are:
    // left right top bottom
    this.camera = new THREE.OrthographicCamera(
        -1, 1, // l r
        1, -1, // t b
        -1, 1);
    this.camera.position.z = 0;

    // set up projector and raycaster, to be used to picking
    this.projector = new THREE.Projector();
    this.raycaster = new THREE.Raycaster();

    // texture filtering options
    // bi-linear filtering and no mip maps
//    var tex_options = {
//        magFilter: THREE.NearestFilter,
//        minFilter: THREE.NearestFilter,
//        generateMipmaps: false
//    };
    var tex_options = {};

    // round up to the nearest int divisable by 4
//    var width4 = width + 4 - width%4;
//    var height4 = height + 4 - height%4;
//    
    // round up to nearest power of 2
    var width4 = Math.pow(2, Math.ceil(Math.log(width) / Math.log(2)));
    var height4 = Math.pow(2, Math.ceil(Math.log(height) / Math.log(2)));
    
    // one over size
    var h = 1.0 / width4;
    var v = 1.0 / height4;
    
    // two render targets on which we will do multiple convolutions filters
    // back and forth.
    this.rtTexturePing = new THREE.WebGLRenderTarget(
        width4, height4, tex_options);
    this.rtTexturePong = new THREE.WebGLRenderTarget(
        width4, height4, tex_options);

    // density texture map
    // this is a 1d colormap to represent the density color on the background
    var dmap = THREE.ImageUtils.loadTexture(FusePool.Landscaping.baseURL + "img/dmap.png");


    // horizontal convolution shader uniforms
    this.uniformsH = {
        sigma: { type: "f", value: 1.4 },
        src_tex: { type: "t", value: this.rtTexturePing },
        pixelSize: { type: "v2", value: new THREE.Vector2(h, v)}
    };
    // horizontal convolution shader itself
    var shaderMaterialPing = new THREE.ShaderMaterial({
        depthTest: false,
        uniforms: this.uniformsH,
        vertexShader: FusePool.Landscaping.Shaders.vertexShader,
        fragmentShader: FusePool.Landscaping.Shaders.fragmentShaderH
    });


    // vertical convolution shader uniforms
    this.uniformsV = {
        sigma: { type: "f", value: 1.4 },
        src_tex: { type: "t", value: this.rtTexturePong },
        dmap_tex: { type: "t", value: dmap },
        pixelSize: { type: "v2", value: new THREE.Vector2(h, v)}
    };
    // vertical convolution shader itself
    var shaderMaterialPong = new THREE.ShaderMaterial({
        depthTest: false,
        uniforms: this.uniformsV,
        vertexShader: FusePool.Landscaping.Shaders.vertexShader,
        fragmentShader: FusePool.Landscaping.Shaders.fragmentShaderV
    });

    this.datapoints = data.rows;
    this.datapointsCount = this.datapoints.length;

    // geometry mesh data, allocate float arrays for
    // both color (rgb) and positions (xyz)
    //var geometry = new THREE.BufferGeometry();
    //this.geometry = geometry;
//    geometry.attributes = {
//        position: {
//            itemSize: 3,
//            array: new Float32Array(this.datapointsCount * 3),
//            numItems: this.datapointsCount * 3
//        },
//        color: {
//            itemSize: 3,
//            array: new Float32Array(this.datapointsCount * 3),
//            numItems: this.datapointsCount * 3
//        }
//    };

    // for convenience
//    var positions = geometry.attributes.position.array;
//    var colors = geometry.attributes.color.array;

    var geometry = new THREE.Geometry();

    // loop all dataPoints
    for (var i = 0; i < this.datapointsCount; i++) {
        // stride of 3 (xyz)
        var pos = 3 * i;
        // load positions from jsondata and translate them
        // positions in json data must not exceed 0..1 for both x and y
//        positions[pos] = (this.datapoints[i].PX)-0.5;
//        positions[pos + 1] = (this.datapoints[i].PY)-0.5;
//        positions[pos + 2] = 0;

        // load colors, init on red
//        colors[pos] = 1;
//        colors[pos + 1] = 0;
//        colors[pos + 2] = 0;
        geometry.vertices.push(new THREE.Vector3(
                this.datapoints[i].PX-0.5, this.datapoints[i].PY-0.5, 0));
        
    }
    // THREE bounds calculation
    geometry.computeBoundingSphere();

    // load dot.png
    this.sprite = THREE.ImageUtils.loadTexture(FusePool.Landscaping.baseURL + "img/dot.png");
    this.sprite.generateMipmaps = true;
    this.sprite.magFilter = THREE.NearestFilter;
    this.sprite.minFilter = THREE.LinearMipMapLinearFilter;
    this.sprite.needsUpdate = true;
    // size of the dot
    this.radius = 30;
    // material used for dot rendering which will be convoluted
    this.dotMaterialConv = new THREE.PointCloudMaterial({
        depthTest: false,
        size: this.radius,
        map: this.sprite,
        sizeAttenuation: false,
        transparent: true,
        // only using red in blur shader
        color: 0xFF0000
    });
    // material used for final dot rendering
    this.dotMaterial = new THREE.PointCloudMaterial({
        depthTest: false,
        size: 10,
        map: this.sprite,
        sizeAttenuation: false,
        transparent: true,
        color: 0x0,
        opacity: 1
//        blending: THREE.AdditiveBlending
        //vertexColors: true
    });

    // add particle system to the main scene
    this.particleSystem = new THREE.PointCloud(geometry, this.dotMaterial);
    this.scene.add(this.particleSystem);
    this.particleSystem.material = this.dotMaterial;
    // plane geometry used to render fullscreen using convolution shader
    var plane = new THREE.PlaneGeometry(width, height);
    // for both shaders we add a full screen quad
    var quadPing = new THREE.Mesh(plane, shaderMaterialPing);
    var quadPong = new THREE.Mesh(plane, shaderMaterialPong);
    // add geometry to both scenes
    this.scenePing.add(quadPing);
    this.scenePong.add(quadPong);

    // initialize the renderer
    this.renderer = new THREE.WebGLRenderer({
        antialias: false,
        clearAlpha: 0,
        clearColor: 0x000000,
        alpha: true
    });
    // resize
    this.renderer.setSize(width, height);
    this.renderer.autoClear = false;

    // return the canvas
    return this.renderer.domElement;
};


/**
 * Namespace to keep all shader code.
 *
 * @author: Daniël van Adrichem <daniel@treparel.com>
 */
FusePool.Landscaping.Shaders = {
    // screen aligned quad
    vertexShader: [
        "varying vec2 pixel;",
        "",
        "void main(void) {",
        "    gl_Position = projectionMatrix * modelViewMatrix * vec4(position, 1.0);",
        "    gl_Position = sign( gl_Position );",
        // pixel will be in range 0..1
        "    pixel = (vec2( gl_Position.x, -gl_Position.y ) + vec2( 1.0 ) ) / vec2( 2.0 );",
        "}"
    ].join('\n'),
    
    fragmentShaderH: [
        "uniform sampler2D src_tex;",
        "uniform vec2 pixelSize;",
        "uniform float sigma;",
        "",
        "varying vec2 pixel;",
        "",
        "void main(void) {",
        "    float h = sigma * pixelSize.x;",
        "    vec4 sum = vec4(0.0);",
        "",
        "    sum += texture2D(src_tex, vec2(pixel.x - 9.0*h, pixel.y) ) * 0.008074244714835564;",
        "    sum += texture2D(src_tex, vec2(pixel.x - 8.0*h, pixel.y) ) * 0.01373475292908177;",
        "    sum += texture2D(src_tex, vec2(pixel.x - 7.0*h, pixel.y) ) * 0.02194807268686863;",
        "    sum += texture2D(src_tex, vec2(pixel.x - 6.0*h, pixel.y) ) * 0.032947959470316014;",
        "    sum += texture2D(src_tex, vec2(pixel.x - 5.0*h, pixel.y) ) * 0.0464640702427165;",
        "    sum += texture2D(src_tex, vec2(pixel.x - 4.0*h, pixel.y) ) * 0.06155489208605796;",
        "    sum += texture2D(src_tex, vec2(pixel.x - 3.0*h, pixel.y) ) * 0.07660630093247092;",
        "    sum += texture2D(src_tex, vec2(pixel.x - 2.0*h, pixel.y) ) * 0.08956183951296363;",
        "    sum += texture2D(src_tex, vec2(pixel.x - 1.0*h, pixel.y) ) * 0.09836443747572207;",
        "    sum += texture2D(src_tex, vec2(pixel.x + 0.0*h, pixel.y) ) * 0.10148685989793388;",
        "    sum += texture2D(src_tex, vec2(pixel.x + 1.0*h, pixel.y) ) * 0.09836443747572207;",
        "    sum += texture2D(src_tex, vec2(pixel.x + 2.0*h, pixel.y) ) * 0.08956183951296363;",
        "    sum += texture2D(src_tex, vec2(pixel.x + 3.0*h, pixel.y) ) * 0.07660630093247092;",
        "    sum += texture2D(src_tex, vec2(pixel.x + 4.0*h, pixel.y) ) * 0.06155489208605796;",
        "    sum += texture2D(src_tex, vec2(pixel.x + 5.0*h, pixel.y) ) * 0.0464640702427165;",
        "    sum += texture2D(src_tex, vec2(pixel.x + 6.0*h, pixel.y) ) * 0.032947959470316014;",
        "    sum += texture2D(src_tex, vec2(pixel.x + 7.0*h, pixel.y) ) * 0.02194807268686863;",
        "    sum += texture2D(src_tex, vec2(pixel.x + 8.0*h, pixel.y) ) * 0.01373475292908177;",
        "    sum += texture2D(src_tex, vec2(pixel.x + 9.0*h, pixel.y) ) * 0.008074244714835564;",
        "",
        "    gl_FragColor = sum;",
        //"    gl_FragColor = vec4(pixel, 0, 1);",
        "}"
    ].join('\n'),

    fragmentShaderV: [
        "uniform sampler2D src_tex;",
        "uniform sampler2D dmap_tex;",
        "uniform vec2 pixelSize;",
        "uniform float sigma;",
        "",
        "varying vec2 pixel;",
        "",
        "void main(void) {",
        "",
        "    float v = sigma * pixelSize.y;",
        "    vec4 sum = vec4(0.0);",
        "",
        "    sum += texture2D(src_tex, vec2(pixel.x, - 9.0*v + pixel.y) ) * 0.008074244714835564;",
        "    sum += texture2D(src_tex, vec2(pixel.x, - 8.0*v + pixel.y) ) * 0.01373475292908177;",
        "    sum += texture2D(src_tex, vec2(pixel.x, - 7.0*v + pixel.y) ) * 0.02194807268686863;",
        "    sum += texture2D(src_tex, vec2(pixel.x, - 6.0*v + pixel.y) ) * 0.032947959470316014;",
        "    sum += texture2D(src_tex, vec2(pixel.x, - 5.0*v + pixel.y) ) * 0.0464640702427165;",
        "    sum += texture2D(src_tex, vec2(pixel.x, - 4.0*v + pixel.y) ) * 0.06155489208605796;",
        "    sum += texture2D(src_tex, vec2(pixel.x, - 3.0*v + pixel.y) ) * 0.07660630093247092;",
        "    sum += texture2D(src_tex, vec2(pixel.x, - 2.0*v + pixel.y) ) * 0.08956183951296363;",
        "    sum += texture2D(src_tex, vec2(pixel.x, - 1.0*v + pixel.y) ) * 0.09836443747572207;",
        "    sum += texture2D(src_tex, vec2(pixel.x, + 0.0*v + pixel.y) ) * 0.10148685989793388;",
        "    sum += texture2D(src_tex, vec2(pixel.x, + 1.0*v + pixel.y) ) * 0.09836443747572207;",
        "    sum += texture2D(src_tex, vec2(pixel.x, + 2.0*v + pixel.y) ) * 0.08956183951296363;",
        "    sum += texture2D(src_tex, vec2(pixel.x, + 3.0*v + pixel.y) ) * 0.07660630093247092;",
        "    sum += texture2D(src_tex, vec2(pixel.x, + 4.0*v + pixel.y) ) * 0.06155489208605796;",
        "    sum += texture2D(src_tex, vec2(pixel.x, + 5.0*v + pixel.y) ) * 0.0464640702427165;",
        "    sum += texture2D(src_tex, vec2(pixel.x, + 6.0*v + pixel.y) ) * 0.032947959470316014;",
        "    sum += texture2D(src_tex, vec2(pixel.x, + 7.0*v + pixel.y) ) * 0.02194807268686863;",
        "    sum += texture2D(src_tex, vec2(pixel.x, + 8.0*v + pixel.y) ) * 0.01373475292908177;",
        "    sum += texture2D(src_tex, vec2(pixel.x, + 9.0*v + pixel.y) ) * 0.008074244714835564;",
        "",
        "    //sum = vec4(0.6);",
        "    gl_FragColor = /*vec4(pixel * vec2(0.6), 0, 0.1) + */texture2D(dmap_tex, vec2(sum.r, 1));",
        "    gl_FragColor.a = 1.0;",
        "}"
    ].join('\n')
};