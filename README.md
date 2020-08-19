# nuxeo-fontoxml
[![Build Status](https://qa.nuxeo.org/jenkins/buildStatus/icon?job=Sandbox/sandbox_nuxeo-fontoxml-master)](https://qa.nuxeo.org/jenkins/job/Sandbox/job/sandbox_nuxeo-fontoxml-master/)


# Table of Content
- [About nuxeo-fontoxml](#about)
- [About the Integration - Requirements](#about-the-integration-requirements)
- [Deployment - Displaying Fonto in the UI](#deployment-displaying-fonto-in-the-ui)
  * [Deployment of Fonto](#deployment-of-fonto)
  * [Displaying Fonto in the UI](#displaying-fonto-in-the-ui)
  * [Adding Logic with an Event Handler](#adding-logic-with-an-event-handler)
  * [Tuning Log Info at Runtime](#tuning-log-info-at-runtime)
- [Configuration](#configuration)
- [Features in the Context of this POC](#features-in-the-context-of-this-poc)
- [Build-Installation](#build-Installation)
- [Support](#support)
- [Licensing](#licensing)
- [About Nuxeo](#about-nuxeo)

## About
nuxeo-fontoxml is a plugin allowing for editing XML files within Nuxeo, using the [FontoXML Editor](https://www.fontoxml.com).

(Click the images for bigger display):

<div>
<img src="https://github.com/nuxeo-sandbox/nuxeo-fontoxml/blob/master/README-Files/Nuxeo-Fonto-1.jpg" width="45%">  
<img src="https://github.com/nuxeo-sandbox/nuxeo-fontoxml/blob/master/README-Files/Nuxeo-Fonto-2.jpg" width="45%">
</div>

### WARNINGS
* This plugin is a **Proof of Concept** (POC), it is not a final product. We want to show Nuxeo can integrate the Fonto XML Editor and interact with it. As a POC it does not handle all and every features a final product will have (see below for more details).

* Also (see below) you need to deploy your custom distribution of Fonto in order to display your XML files


<a name="about-the-integration-requirements"></a>
## About the Integration - Requirements

* See Fonto API, especially in its [Integrate with a CMS](https://documentation.fontoxml.com/editor/latest/integrate-with-a-cms-3099086.html) part.
* Fonto is distributed as a static Single Page Application that must be served by Nuxeo (so, no CORS, user is already authenticated, etc.)
* The Fonto distribution must have been built with the different schemas you plan to use: Contact FontoXML for this purpose, Nuxeo does not provide Fonto XML builds.
* This also means **the Fonto Editor does not display all and every XML** files, even if well formatted and embedding its own DTD. You will still need a distribution of Fonto handling your specific schemas.

_Note_: In our POC, we tested with a distribution that includes DITA capabilities.


<a name="deployment-displaying-fonto-in-the-ui"></a>
## Deployment - Displaying Fonto in the UI

#### Deployment of Fonto
Assuming you have a distribution of FontoXML, you must make it available for the first call, when it is first loaded

* So, a distribution of FontoXML must be deployed on your server.
  * We cannot include a Fonto distribution with this plugin.
  * _But_ of course, you will easily build a Marketplace Package deploying _your_ specific Fonto XML distribution wherever you want in the `nuxeo.war` folder when Nuxeo starts.
* For quick tests though, once you have a running server, you can just manually drop the FontoXML distribution inside  `nuxeo.war`
  * Again, this is OK in the context of a POC, certainly not in production where best practices and ease-of-use require to deploy the Fonto distribution in a Marketplace Package.
  * For example, in our testing we dropped it in `nxserver/nuxeowar/ui` and named the folder containing our distribution `FontoXML`, so we have:

```
nxserver
  nuxeo.war
    ui
      FontoXML
        assets
        index.html
        . . .
```

### Displaying Fonto in the UI

_Notice_: A full example using a nuxeoo-file-view-layout can be found in the "README-Files" folder of this GitHub repository. You can copy/paste its content in your Studio project. It displays the Fonto XML Editor if the current document holds an XML blob. Else, it displays the default Nuxeo Viewer.

Here is a brief summary:

We added an iFrame to the default nuxeo-file-view-layout document, just adding:

```
<nuxeo-card>
  <iframe id="fontoxml"
          frameborder="0"
          style="width: 100%;height:75vh;"
          src=[[url]]>
  </iframe>
</nuxeo-card>  
```
 `url` is dynamically calculated once the `document` and the `user` are loaded. Among the misc. parameters expected, make sure to fill the `editSessionToken` as expected by the plugin in the backend. It mus be a JSON with 2 fields:
 
 * `mainDocId`: The UUID of the current document, or the UUID of a Folderish document
   * This is used when Font asks to create a new document.
   * It will be created:
     * In the mainDocId document if it is a folderish
     * Or in the first parent of the mainDocId if it is not.
     * Also, the service will allow for calling an automation chain with this mainDocId parameter, so it returns a folder where to create the new document, based on business logic.
 * `unicityToken`: Any number that makes `UUID + unicityTocken` unique (typically, use `Date.now()`)
 
 The result is something like:
 
 ```
• • •
// editSessionTocken is used by the plugin in the backend, it is required to pass these values:
let editSessionToken = {
  "mainDocId": this.document.uid,
  "unicityToken": Date.now()
}
// See https://documentation.fontoxml.com/editor/latest/invocation-of-the-fonto-editor-30015537.html
let queryParams = {
  "documentIds": [this.document.uid],
  "cmsBaseUrl": "http://localhost:8080/nuxeo/fontoxml",
  "user": {
    "id": this.user.id,
    "displayName": this._getUserName(this.user)
  },
  "editSessionToken": JSON.stringify(editSessionToken),
  "autosave": false,
  "heartbeat": 60 // => must implement the GET /heartbeat endpoint
}
/* Other possible parameters:
requestTimeoutInSeconds
useEmbeddedMode
*/
let url = window.location.origin + "/nuxeo/ui/FontoXML?scope=" + JSON.stringify(queryParams);
url = encodeURI(url);
this.url = url;
 • • •
 ```

* **WARNING**: Even if Fonto is displayed in an iFrame, we do not use the _Fonto XML iFrame Connector_, we use the _Standard Connector_, that makes HTTP requests to Nuxeo

* In our testing, we set `autoSave` to `false` when initializing the Fonto Editor. `auytoSave`, when `true`, sends a request 2 seconds after each edit.
  * Fonto allows for detecting it's "auto save" so we can optimize the database load, maybe, but still. Saving every n seconds does not really scale. => In this POC it will work, because you usually test a POC on very few documents :-)
  * This POC always save when requested to do so

### Adding Logic with an Event Handler
Every time the document is saved in the repository (either because you set the `autoSave` option to `true` or because the user clicked the "Save" button in the Fonto UI), the plugin:

* Fires the `documentModifiedByFontoXML` event.
* Adds the `isFontoAutoSave` (boolean, true/false) property in the document context (accessible via the `DopcumentModel#putContextData` API)

You can catch this event and add more logic if you need to do so:

* Either use a [Java Listener](https://doc.nuxeo.com/nxdoc/events-and-messages/)
* Or use an [Event Handler](https://doc.nuxeo.com/studio/event-handlers/) (using Automation) with Studio:
  * Add the "documentModifiedByFontoXML" event to Studio registry
  * Create an Event Handler that listens to this event and runs an Automation chain (regular or JavaScript automation).
  * Notice: In automation, the input will be the document. If you want to check the autosave flag, in Automation Scriptin you can check `if(input.getDoc().getContextData("isFontoAutoSave"))) ...`

### Tuning Log Info at Runtime
The plugin writes some warnings in server.log when needed. For more informations you can activate the info level in the Log4j configuration. This will log more details (like the request received, the parameters, etc.). In order to do so:

* On your server, modify the `log4j2.xml` file, at `{Nuxeo Home}/lib`
  * Find the `<Loggers>` part
  * Add the following:

```
<Logger name="nuxeo.fontoxml.servlet.FontoXMLServlet" level="info" />
<Logger name="nuxeo.fontoxml.servlet.DocumentBrowser" level="info" />
```
  * Save
* No need to restart the server, changes in `log4j2.xml` are handled dynamically
* Which means, you also can change the `level` back to `"warn"` when you don't need the info anymore (no need to restart Nuxeo)

## Configuration

When Fonto calls `GET /asset`, it is possible to return a blob evaluated by configuration (not hard coded in the plugin).

An XML contribution can be added to dynamically decide which blob to return:

* It can be returned by an Automation Chain that is called back by the service
* If there is no chain defined, or it returned `null`, we use the name of a rendition
* If there is no rendition name defined, or no rendition of this name in `picture:views`, we use an xpath
* If there is no xpath defined, or if the blob at this xpath is null, we give up and return `file:content`

To contribute the service, create a new XML contribution in Studio, and paste/adapt the following, which is the default contribution:

```
<extension target="com.nuxeo.fontoxml.FontoXMLService" point="configuration">
  <configuration>
    <renditionCallbackChain></renditionCallbackChain>
    <defaultRendition>OriginalJpeg</defaultRendition>
    <renditionXPath></renditionXPath>
  </configuration>
</extension>
```

* In `renditionCallbackChain`, set the ID of an automation chain
  * Optional. Default value is `""` (no chain)
  * Remember the ID of a scripting automation starts with `javascript.`
  * This chain receives the image document as input and must return a blob (or `null`)
  * This allows for dynamically deciding what to return (maybe based on metadata)
  * Example:

```
// input: document, output: blob
function run(input, params) {
  var rendition = null;
  
  // In this example, we use a the dc:expired field that tells us if the image rights have expired
  // If yes, we return a precalculated watermarked rendition, else we return null and the system will
  // get the "OriginalJpeg"
  if(input["dc:expired"] && input["dc:expired"] > (new Date()).toISOString()) {
    rendition = Picture.getView(input, {"vieName": "Watermarked"});
    // If this rendition does not exist yet, lets watermark dynamically
    // Find an example of how to watermark here: https://doc.nuxeo.com/nxdoc/how-to-contribute-picture-conversions/
    if(!rendition ) {
      rendition = Blob.RunConverter(
                    input["file:content"], {
                      "converter": "WatermarConverterToJpeg",
                      "parameters": "targetFileName="copyrighted.jpeg"
                   });
    }
  }
  
  // If null, the service will get the defaultRendition
  return rendition;
}
```

* In `defaultRendition`, set the name of a rendition
  * Optional. Default value is "OriginalJpeg"
  * This is the `title` of a rendition in the `picture:views` field

* In `xpath`, put the xpath of a field holding the blob to use for the asset



## Features in the Context of this POC
Now, a not-really-started, not-finished :-) and _unordered_ list of items in the context of this POC

* **Implemented endpoints** (see `FontoXMLServlet`):
  * (**IMPORTANT REMINDER** (in case we did not enough stress this point :-)): This is a POC implementation, not a final product)
  * `GET /document`
  * `GET /asset`
  * `GET /asset/preview`
  * `GET /heartbeat`
  * `POST /browse`
  * `POST /asset`
  * `POST /document/state`
  * `PUT /document`
  * `PUT /document/lock`
* This POC does not implement versioning policy:
  * This should be done in final product (when to create version(s) automatically, likely a configuration parameter)
  * Also during browsing: You may want to browse only zn un-mutable version of an image, for example
* We don't really make usage of `editSessionToken` and `revisionId`
* Locking is done per Fonto request. So, if the document was already locked and Fonto asks us to unlock it at some point, we do unlock it.
  * This will likely need to be optimized in the final product
  * Also, Fonto sends a `POST /document/state` to get lock info: A custom build requiring this info less often would be good, and the POC just return cached info, we don't re-calculate the lock every time Font is asking us.
* This POC Mainly assumes current user can at least READ the parents of the current document.
* When **browsing Nuxeo**:
  * _We only handle default document types_ ("File", "Picture", ...) <br/> => Room for improvement and configuration in a final product to handle custom document types.
  * This POC **does not handle pagination** (as the `POST /browse` end point parameters could allow)
  * Also, we **assume an XML Blob always has "text/xml" mime-type**
  * We do not handle a "document-template" type in the context of this POC.
  * The algorithm must be modified in the final product. The search should handle both the `assetTypes` and `resultTypes` passed by Fonto. The POC filters afterward (not optimized)
  * We ignore the "sort" parameter => always sorting by title (this also could be configuration)
  * **This POC assumes the user can READ root/domain/etc.**
* We rarely return a 403, not authorized. Nuxeo security policy is that if a user can't read a document, they should not even know it exists. So, when trying to access a document a 404 is returned. Some Fonto API requires a 403 for messaging though.
* Maybe pre-calculated renditions should be implemented, to be used when browsing.<br /> Fonto's API documentation requires a "thumbnail" rendition be exactly 128x128 and a "web" rendition to be max 1024. In this POC, we get the thumbnail (so it's easy and done in one line of code) and resize it accordingly. This is not optimized at all.
* **No unit test** (yet...)
* Simultaneous loading of several documents is not implemented (this POC always loads one by one)
* The `documentContext` is currently mainly used as a cache for the POST /document/state regular calls. This object looks very interesting and should likely be used in the final product.
* **TO BE EXPLORED**
    * fulltext index fails on the test XML documents:

```
Could not extract fulltext of file [...] org.nuxeo.ecm.core.convert.api.ConversionException: Error during XML2Text conversion
```

* . . .
 

# Build-Installation

```
git clone https://github.com/nuxeo-fontoxml.git
cd nuxeo-fontoxml

mvn clean install
```

Installation:

* From the public marketplace, just `./nuxeoctl mp-install -s nuxeo-fonto-xml-integration` (`-s` switch to allow for installing a snapshot version on a release Nuxeo server))
* After building the plugin locally: `./nuxeoctl mp-install /path/to+nuxeo-fonto-xml/nuxeo-fontoxml-package/target/nuxeo-fontoxml-package-10.10-SNAPSHOT.zip`

# Support

**These features are not part of the Nuxeo Production platform, they are not supported**

These solutions are provided for inspiration and we encourage customers to use them as code samples and learning resources.

This is a moving project (no API maintenance, no deprecation process, etc.) If any of these solutions are found to be useful for the Nuxeo Platform in general, they will be integrated directly into the platform, not maintained here.

# Licensing

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

# About Nuxeo

Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile, innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and cutting-edge content-oriented applications. Combining a powerful application development environment with SaaS-based tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most recognizable brands including Verizon, Electronic Arts, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is headquartered in New York and Paris.

More information is available at [www.nuxeo.com](http://www.nuxeo.com).  
