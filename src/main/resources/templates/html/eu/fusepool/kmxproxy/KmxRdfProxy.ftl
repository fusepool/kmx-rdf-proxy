<@namespace ont="http://example.org/service-description#" />
<@namespace ehub="http://stanbol.apache.org/ontology/entityhub/entityhub#" />
<@namespace cc="http://creativecommons.org/ns#" />
<@namespace dct="http://purl.org/dc/terms/" />

<html>
  <head>
    <title>KMX Service proxy - Apache Stanbol</title>
    <link type="text/css" rel="stylesheet" href="styles/multi-enhancer.css" />
  </head>

  <body>
    <h1>Example usage:</h1>

    <p>This is a work in progress and subject to change.</p>
    <p>Send an HTTP POST request to /kmxrdfproxy/ranking with the following JSON object as body:</p>
    <p>Also make sure the Accept header contains application/rdf+xml</p>
    <p>

{<br>
"contentStoreUri": "http://localhost:8080",<br>
"contentStoreViewUri": "http://localhost:8080",<br>
"searchs": ["searchterm"],<br>
"labels": { "doc uri": "Positive", "doc uri2", "Negative},<br>
}<br>
    </p>
  </body>
</html>

