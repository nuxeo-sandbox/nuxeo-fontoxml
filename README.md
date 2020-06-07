# nuxeo-fontoxml

## About the Plugin
nuxeo-fontoxml is a plugin allowing for editing XML file within Nuxeo, using the [FontoXML Editor](https://www.fontoxml.com).


### WARNINGS
1. This is **Work In Progress**.

2. Also, this plugin is a **Proof of Concept** (POC), it is not a final product. We want to show Nuxeo can integrate the Fonto XML Editor and interact with it. As a POC it does not handle all and every features a final product will have (see below for more details).


# Table of Content
- [About the Integration - Requirements](#about-the-integration-requirements)
- [Deployment - Displaying Fonto in the UI](#deployment-displaying-fonto-in-the-ui)
- [Features in the Context of this POC](#features-in-the-context-of-this-poc)
- [Build](#build)
- [Support](#support)
- [Licensing](#licensing)
- [About Nuxeo](#about-nuxeo)


<a name="about-the-integration-requirements"></a>
## About the Integration - Requirements

* See Fonto API, especially in its [Integrate with a CMS](https://documentation.fontoxml.com/editor/latest/integrate-with-a-cms-3099086.html) part.
* Fonto is distributed as a static Single Page Application that must be served by Nuxeo (so, no CORS, user is already authenticated, etc.)
* The Fonto distribution must have been built with the different schemas you plan to use: Contact FontoXML for this purpose, Nuxeo does not provide Fonto XML builds.

_Note_: In our POC, we tested with a distribution that includes DITA capabilities.


<a name="deployment-displaying-fonto-in-the-ui"></a>
## Deployment - Displaying Fonto in the UI

* Assuming you have a distribution of FontoXML, you must make it available for the first call, when it is first loaded
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
  * For very quick test, we added an iFrame to the default nuxeo-file-view document, just adding:

```
<nuxeo-card>
  <iframe id="fontoxml"
          frameborder="0"
          style="width: 100%;height:75vh;"
          src=[[url]]>
  </iframe>
</nuxeo-card>  
```
 `url` is dynamically calculated once the `document` and the `user` are loaded. The result is something like:
 
 ```
• • •
// See https://documentation.fontoxml.com/editor/latest/invocation-of-the-fonto-editor-30015537.html
let queryParams = {
  "documentIds": [this.document.uid],
  "cmsBaseUrl": "http://localhost:8080/nuxeo/fontoxml",
  "user": {
    "id": this.user.id,
    "displayName": this._getUserName(this.user)
  },
  "editSessionToken": this.document.uid, // required not yet sure how to use it
  "autosave": false,
  "heartbeat": 60 // => must implement the GET /heartbeat endpoint
}
/* Other possible parameters:
requestTimeoutInSeconds
useEmbeddedMode
*/
let url = "http://localhost:8080/nuxeo/ui/FontoXML?scope=" + JSON.stringify(queryParams);
url = encodeURI(url);
this.url = url;
 • • •
 ```

* **WARNING**: Even if Fonto is displayed in a iFrame, we do not use the _Fonto XML iFrame Connector_, we use the _Standard Connector_, that makes HTTP requests to Nuxeo

* We recommend setting `autoSave` to false when initializing the Fonto Editor, it sends a lot of request (2 seconds after each edit.
  * Fonto allows for detecting it's "auto save" so we can optimize the database load, maybe, but still. Saving every n seconds does not really scale. => In this POC it will work, because you usually test a POC on very few documents :-)

## Features in the Context of this POC
Now, a not-really-started, not-finished :-) and _unordered_ list of items in the context of this POC

  * **Implemented endpoints** (see `FontoXMLServlet`):
    * (**IMPORTANT REMINDER** (in case we did not enough stress this point :-)): This is a POC implementation, not a final product)
    * `GET /document`
    * `GET /asset/preview`
    * `GET /heartbeat`
    * `POST /browse`
    * `POST /document/state`
    * `PUT /document`
    * `PUT /document/lock`
  * This POC does not implement versioning policy. This should be done in final product (when to create version(s) automatically, likely a configuration parameter)
  * We don't really make usage of `editSessionToken` and `revisionId`
  * Locking is done per Fonto request. So, if the document was already locked and Fonto asks us to unlock it at some point, we do unlock it.
    * This will likely need to be optimized in the final product
    * Also, Fonto sends a `POST /document/state` to get lock info: A custom build requiring this info less often would be good, and the POC just return cached info, we don't re-calculate the lock every time Font is asking us.
  * When **browsing Nuxeo**:
    * _We only handle default document types_ ("File", "Picture", ...) <br/> => Room for improvement and configuration in a final product to handle custom document types.
    * This POC **does not handle pagination** (as the `POST /browse` end point parameters could allow)
    * Also, we **assume an XML Blob always has "text/xml" mime-type**
    * We do not handle a "document-template" type in the context of this POC.
    * We ignore the "sort" parameter => always sorting by title (this also could be configuration)
    * **This POC assumes the user can READ root/domain/etc.**
  * We rarely returns a 403, not authorized. Nuxeo security policy is that if a user can't read a document, they should not even know it exists. So, when trying to access a document a 404 is returned. Some Fonto API requires a 403 for messaging though.
  * Maybe pre-calculated renditions should be implemented, ti be used when browsing.<br /> Fonto's API documentation requires a thumbnail be exactly 128x128 and a "web" rendition should be max 1024. In this POC, we get the thumbnail (so it's easy and done in one line of code) and resize it accordingly. This is not optimized at all.
  * **No unit test** (yet...)
  * Simultaneous loading of several documents is not implemented (this POC always loads one by one)
  * The `documentContext` is currently mainly used as a cache for the POST /document/state regular calls. This object looks very interesting and should likely be used in the final product.
  * . . .
 

# Build

```
git clone https://github.com/nuxeo-fontoxml.git
cd nuxeo-fontoxml

mvn clean install
```

# Support

**These features are not part of the Nuxeo Production platform, they are not supported**

These solutions are provided for inspiration and we encourage customers to use them as code samples and learning resources.

This is a moving project (no API maintenance, no deprecation process, etc.) If any of these solutions are found to be useful for the Nuxeo Platform in general, they will be integrated directly into the platform, not maintained here.

# Licensing

[Apache License, Version 2.0](http://www.apache.org/licenses/LICENSE-2.0)

# About Nuxeo

Nuxeo dramatically improves how content-based applications are built, managed and deployed, making customers more agile, innovative and successful. Nuxeo provides a next generation, enterprise ready platform for building traditional and cutting-edge content-oriented applications. Combining a powerful application development environment with SaaS-based tools and a modular architecture, the Nuxeo Platform and Products provide clear business value to some of the most recognizable brands including Verizon, Electronic Arts, Sharp, FICO, the U.S. Navy, and Boeing. Nuxeo is headquartered in New York and Paris.

More information is available at [www.nuxeo.com](http://www.nuxeo.com).  
