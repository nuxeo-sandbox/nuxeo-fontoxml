# nuxeo-fontoxml

### WARNING
This is **Work In Progress**. Using GitHub as backup for now.

## About
nuxeo-fontoxml is a _Proof of Concept_, a plugin allowing for editing XML file within Nuxeo, using the [FontoXML Editor](https://www.fontoxml.com) and its support for DITA documents.

As it is Work In Progress, here is a very quick unordered list of features/ToDo:

* A build of FontoXML must be deployed on your server, it cannot be included in the Marketplace Päckage of the plugin.
  * Easiest way is to just drop it in `nxserver/nuxeowar/ui`.
  * In the following example/doc, we named the folder containing the FontoXML distribution `FontoXML`, so we have:

```
nxserver
  nuxeo.war
    ui
      FontoXML
        assets
        index.html
        . . .
```

* We call it after embedding Fonto in an iFrame
  * WARNING: We do not use the Fonto XML iFrame Connector, we use the Standard Connector, that makes HTTP requests to Nuxeo

* We recommend setting `autoSave` to false when initializing the Fonto Editor, it sends a lot of request (2 seconds after each edit.
  * Fonto allows for detecting it's "auto save" so we can optimize the database load, maybe, but still. Saving every n seconds does not really scale. => In this POC it will work, because you usually test a POC on very few documents :-)

* Now a not-really-started and not-finished :-) list of items in the context of this POC
  * *IMPORTANT*: This is a POC, not a final product
  * We don't really make usag `editSessionToken` and `revisionId`
  * Locking is done per Fonto request. So, if the document was alrerady locked and Fonto asks us to unlock it at some point, we do unlock it.
    * This will likely need to be optimize in the final product
    * Also, Fonto sends a POST /document/state to get lock info: A custom build requiring this info less often would be good, and the POC just return cached info, we don't re-calculate the lock every time Font is asking us.
  * No unit test
    * Will likely require to mockup Fonto requests
  * Implemented endpoints
    * GET /document
    * POST /document/state
    * PUT /document
    * PUT /document/lock
  * Simultaneous loading of several documents is not implemented (this POC always loads one by one)
  * Idea of usage for the `documentContext`: Add a boolean that will give the lock state of the document at first load. So if it was already locked, we don't release the lock when asked by Fonto (but tell it it was released)
  * . . .
 
 (quite more to do, so far as of 2020-06-05, it works with a simple DITA document (no references, no relations)

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
