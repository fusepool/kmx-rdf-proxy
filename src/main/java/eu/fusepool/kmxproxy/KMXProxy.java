package eu.fusepool.kmxproxy;

import com.treparel.kmxclient.KMXClient;
import java.io.IOException;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.UriInfo;
import org.apache.clerezza.jaxrs.utils.TrailingSlash;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.ontologies.RDF;
import org.apache.clerezza.rdf.utils.GraphNode;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.stanbol.commons.indexedgraph.IndexedMGraph;
import org.apache.stanbol.commons.web.viewable.RdfViewable;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import eu.fusepool.ecs.core.ContentStore;
import eu.fusepool.ecs.core.ContentStoreImpl;
import eu.fusepool.ecs.ontologies.ECS;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.logging.Level;
import org.apache.clerezza.rdf.core.BNode;
import org.apache.clerezza.rdf.core.Resource;
import org.apache.clerezza.rdf.core.impl.TypedLiteralImpl;
import org.apache.clerezza.rdf.ontologies.SIOC;
import org.apache.clerezza.rdf.utils.RdfList;
import org.codehaus.jettison.json.JSONArray;
import scala.actors.threadpool.Arrays;

/*
 * TODO use the OSGi properties for configuration
 * 
 */

/**
 * Manages communication with a KMX service and transformation of responses
 */
@Component(immediate=true)
@Service({Object.class, KMXProxy.class})
@Property(name = "javax.ws.rs", boolValue = true)
@Path("kmxrdfproxy")
public class KMXProxy {
//    public static final String DEFAULT_WEBSERVICE_URL = "http://192.168.1.87:9090/kmx/api/v1/";
//    @Property(value = DEFAULT_WEBSERVICE_URL)
//    public static final String WEBSERVICE_URL = "eu.fusepool.kmxproxy.serverURL";
    @Property()
    public static final String WEBSERVICE_USERNAME = "eu.fusepool.kmxproxy.serverUsername";
    @Property()
    public static final String WEBSERVICE_PASSWORD = "eu.fusepool.kmxproxy.serverPassword";

    
    /**
     * Using slf4j for logging
     */
    private static final Logger log = LoggerFactory.getLogger(KMXProxy.class);
        
    @Reference
    private ContentStore ecs;
    
//    @Reference
//    private ContentItemFactory contentItemFactory;
    
    private KMXClient kmxClient;
    
    @Activate
    protected void activate(ComponentContext context) {
        log.info("The kmx proxy service is being activated");
        Dictionary<String, Object> properties = context.getProperties();
        try {
            kmxClient = new KMXClient();
//                    (String) properties.get(WEBSERVICE_USERNAME),
//                    (String) properties.get(WEBSERVICE_PASSWORD));
        } catch (IOException ex) {
            java.util.logging.Logger.getLogger(KMXProxy.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    @Deactivate
    protected void deactivate(ComponentContext context) {
        log.info("The kmx proxy service is being deactivated");
    }
    
    /**
     * Returns a ranked (ordered) result graph 
     * No query params, a JSON object is expected in the body
     */
    @POST
    @Path("ranking")
    public RdfViewable rankingPriviledged(@Context final UriInfo uriInfo, final String data) throws Exception {
        try {
            return AccessController.doPrivileged(new PrivilegedExceptionAction<RdfViewable>() {
                public RdfViewable run() throws Exception {
                    return ranking(uriInfo, data);
                }
            });
        } catch (PrivilegedActionException e) {
            throw e.getException();
        }        
    }
    
    /**
     * 
     * example data JSON Object:
     * {
     *  "contentStoreUri": "http://example.com/",
     *  "contentStoreViewUri": "http://example.com/",
     *  "subjects": ["http://example.com/"],
     *  "searchs": ["a", "b"],
     *  "items": 500,
     *  "offset": 0,
     *  "maxFacets": 1,
     *  "labels": { "doc uri": "Positive", "doc uri2": "Negative"},
     * }
     * When fields are not provided, the shown value is the default. Except for
     * the labels, which has an empty list as default.
     * 
     * contentStoreUri The IRI of the content store to use
     * contentStoreViewUri The IRI that shall be assigned to the returned view
     * subjects The dc:subjects the matching content items shall have
     * searchs The search patterns the matching documents shall satisfy
     * items the number of items to return
     * offset the position at which to start items (for pagination)
     * maxFacets the maximum number of facets the result shall contain
     */
    @POST
    @Path("ranking_auth")
    public RdfViewable ranking(@Context final UriInfo uriInfo, final String data)
            throws JSONException, IOException, Exception {
        TrailingSlash.enforcePresent(uriInfo);
        log.info("ranking: " + data);
        final String resourcePath = uriInfo.getAbsolutePath().toString();
        final UriRef contentUri = new UriRef(resourcePath);
        JSONObject root;
        try {
            root = new JSONObject(data);
        } catch (org.codehaus.jettison.json.JSONException ex) {
            String msg = ex.getMessage();
            msg += "\n\n" + data.length() + " characters of body data received: " + data;
            throw new Exception(msg);
        }
        
        // get original search parameters from json object
        final UriRef contentStoreUri = new UriRef(
                root.getString("contentStoreUri"));
        final UriRef contentStoreViewUri = new UriRef(
                root.getString("contentStoreViewUri"));
        final Integer items = root.has("items") ? root.getInt("items") : 100;
        final Integer offset = root.has("offset") ? root.getInt("offset") : 0;
        final Integer maxFacets = root.has("maxFacets") ? root.getInt("maxFacets") : 10;
        final List<String> searchs = new ArrayList<String>();
        if (root.has("searchs")) {
            final JSONArray jsonArraySearchs = root.getJSONArray("searchs");
            for (int i = 0; i < jsonArraySearchs.length(); i++) {
                searchs.add(jsonArraySearchs.getString(i));
            }
        }
        final List<UriRef> subjects = new ArrayList<UriRef>();
        if (root.has("subjects")) {
            final JSONArray jsonArraySubjects = root.getJSONArray("subjects");
            for (int i = 0; i < jsonArraySubjects.length(); i++) {
                subjects.add(new UriRef(jsonArraySubjects.getString(i)));
            }
        }
        List<Map<String,String>> labels = new ArrayList<Map<String, String>>();
        if (root.has("labels")) {
            final JSONObject jsonLabels = root.getJSONObject("labels");
            Iterator<?> keys = jsonLabels.keys();

            while (keys.hasNext()) {
                String docName = (String) keys.next();
//                if( jsonLabels.get(key) instanceof String ){ }
                String label = jsonLabels.getString(docName);
                Map<String, String> item;
                item = new HashMap<String, String>();
                item.put("DOCNAME", docName);
                item.put("Label", label);
                labels.add(item);
            }
        } else {
            throw new Exception("Can't create classifier without training labels. Please sepecify labels.");
        }
        
        Collection<UriRef> types = Collections.EMPTY_LIST;
        
        // redo search on ECS
        GraphNode contentStoreView = ecs.getContentStoreView(
                contentStoreUri,
                contentStoreViewUri,
                subjects,
                types,
                searchs,
                items,
                offset,
                maxFacets,
                true);

        Set<String> colNames = new HashSet<String>();
        colNames.add("DOCNAME");
        colNames.add("doc_text");
        // create a new dataset
        JSONObject response = kmxClient.createDataset(colNames);
        Integer dataset_id = (Integer) response.get("dataset_id");

        // get results
        Iterator<GraphNode> nodesIt = contentStoreView.getObjectNodes(ECS.contents);
        if (!nodesIt.hasNext()) {
            throw new Exception("Search on ECS yielded no results.");
        }
        GraphNode contentList = nodesIt.next();
        while (!contentList.getNode().equals(RDF.nil)) {
            Map<String, String> doc = describeContent(contentList.getObjectNodes(RDF.first).next());
            // add the doc to the dataset
            kmxClient.addItemToDataset(dataset_id, doc);
            contentList = contentList.getObjectNodes(RDF.rest).next();
        }

        response = kmxClient.createWorkspace();
        Integer workspace_id = (Integer) response.get("object_id");
        HashMap<String, Integer> features = new HashMap<String, Integer>();
        features.put("DOCNAME", 0);
        features.put("doc_text", 1);
        kmxClient.addDatasetToWorkspace(workspace_id, dataset_id, features);
        
        response = kmxClient.listItemsInDataset(dataset_id);
        JSONArray arr = response.getJSONArray("array");
        HashMap<Integer, String> id2docname = new HashMap<Integer, String>();
        for (int i=0; i < arr.length(); i++) {
            JSONArray pair = arr.getJSONArray(i);
            int id = pair.getInt(0);
            String docname = pair.getString(1);
            id2docname.put(id, docname);
        }
        HashMap<String, Integer> docname2id = new HashMap<String, Integer>();
        for (Map.Entry pair : id2docname.entrySet()) {
            docname2id.put((String)pair.getValue(), (Integer)pair.getKey());
            System.out.println((String)pair.getValue());
        }
            
        String stoplist = kmxClient.createStoplist().getString("stoplist_name");
        String[] stopWords = {"a", "about", "after", "all", "also", "an", "and",
            "any", "are", "as", "at", "be", "because", "been", "but", "by",
            "can", "co", "corp", "could", "for", "from", "had", "has", "have",
            "he", "her", "his", "if", "in", "inc", "into", "is", "it", "its",
            "last", "more", "most", "mr", "mrs", "ms", "mz", "no", "not", "of",
            "on", "one", "only", "or", "other", "out", "over", "s", "says",
            "she", "so", "some", "such", "than", "that", "the", "their",
            "there", "they", "this", "to", "up", "was", "we", "were", "when",
            "which", "who", "will", "with", "would"};
        List<String> words = Arrays.asList(stopWords);

        kmxClient.AddWordsToStoplist(stoplist, words);
        
        // set training docs
        HashMap<String, String> labelMap = new HashMap<String, String>();
        for (Map<String,String> item: labels) {
            String docname = item.get("DOCNAME");
            String label = item.get("Label");
            Integer id = docname2id.get(docname);
            // when null a label was passed in for a docname that is not part of the dataset
            if (id != null) {
                labelMap.put(id.toString(), label);
                System.out.println(id.toString() + " " + label);
            }
        }
        
        kmxClient.labelWorkspaceItems(workspace_id, labelMap);
                
        // make model
        HashMap<String, String> settingsMap = new HashMap<String, String>();
        settingsMap.put("STOPLIST", stoplist);
        settingsMap.put("MAX_FEATURES", "750");
        Integer model_id = kmxClient.createSVMModel(workspace_id, settingsMap).getInt("object_id");
        
        // apply model
        response = kmxClient.applySVMModelToWorkspace(model_id, workspace_id);
        // example output: 
//       {"1":{"Prob2":16.725700000000003,"HypCat1":"Positive","HypCat2":"Negative","Prob1":83.2743},
//        "2":{"Prob2":27.712999999999994,"HypCat1":"Positive","HypCat2":"Negative","Prob1":72.287},
//        "3":{"Prob2":21.5663,"HypCat1":"Positive","HypCat2":"Negative","Prob1":78.4337},
//        "4":{"Prob2":83.2743,"HypCat1":"Positive","HypCat2":"Negative","Prob1":16.7257},
//        "5":{"Prob2":71.5942,"HypCat1":"Positive","HypCat2":"Negative","Prob1":28.4058},
//        "6":{"Prob2":61.4904,"HypCat1":"Positive","HypCat2":"Negative","Prob1":38.5096}}

        Iterator<?> rkeys = response.keys(); // doc_ids
        
        // tree map is a rb-tree
        SortedMap<Double, String> rankedDocnames = new TreeMap<Double, String>(Collections.reverseOrder());
        
        String probKey = determineProbKey(response);
        
        while (rkeys.hasNext()) {
            String docId = (String) rkeys.next();
            JSONObject jsonProb = response.getJSONObject(docId);
            Integer docIdInt = Integer.parseInt(docId);
            String docName = id2docname.get(docIdInt);
            Double posProb = jsonProb.getDouble(probKey);
//            System.out.println(docName + ":" + posProb);
            rankedDocnames.put(posProb, docName);
        }
        
        // either sort this list (list of all content uris):
//        List<Resource> contentNodesList = contentStoreView.getObjectNodes(ECS.contents).next().asList();
        // OR make a new one
        BNode myNewListResource = new BNode();
        Iterator<GraphNode> contentNodesIt = contentStoreView.getObjectNodes(ECS.contents);
        GraphNode contentList2 = contentNodesIt.next();
        contentStoreView.deleteProperties(ECS.contents);
        contentStoreView.addProperty(ECS.contents, myNewListResource);
        List<Resource> rdfList = new RdfList(myNewListResource, contentStoreView.getGraph());
        for (Map.Entry pairs : rankedDocnames.entrySet()) {
            // key hold probability score
            // value is the docname, which is the url without < > surrounding
//            System.out.println(pairs.getKey() + ": " + pairs.getValue());
            GraphNode node = findGraphNode((String)pairs.getValue(), contentList2);
//            System.out.println(node);
            // strip its sioc content
            node.deleteProperties(SIOC.content);
            // add classification score
            node.addProperty(
                new UriRef("http://www.w3.org/2001/XMLSchema#double"),
                new TypedLiteralImpl(pairs.getKey().toString(),
                    new UriRef("http://www.w3.org/2001/XMLSchema#double")));
            rdfList.add(node.getNode());
        }

        return new RdfViewable("KmxRdfProxy", contentStoreView, KMXProxy.class);
    }

    private String determineProbKey(JSONObject jsonResponse) throws JSONException, Exception {
        // inner in this form: 
        // {"Prob2":61.4904,"HypCat1":"Positive","HypCat2":"Negative","Prob1":38.5096}
        Iterator<?> docIds = jsonResponse.keys();
        String docId = (String)docIds.next();
        JSONObject inner = jsonResponse.getJSONObject(docId);
        // find positive hypcat
        Iterator<?> keys = inner.keys();
        while (keys.hasNext()) {
            String key = (String)keys.next();
            if (key.startsWith("HypCat") && "Positive".equals(inner.getString(key))) {
                // get number from hypcat and return Prob%d
                String intStr = key.substring(6);
                return "Prob" + intStr;
            }
        }
        throw new Exception("KMX response object error.");
    }
    
    private GraphNode findGraphNode(String docName, GraphNode contentList) throws Exception {
        //GraphNode contentList = nodesIt.next();
        while (!contentList.getNode().equals(RDF.nil)) {
//            Map<String, String> doc = describeContent(contentList.getObjectNodes(RDF.first).next());
            GraphNode contentNode = contentList.getObjectNodes(RDF.first).next();
            String nodeName = contentNode.getNode().toString();
            // strip < and >
            nodeName = nodeName.substring(1, nodeName.length() - 1);
            if (nodeName.equals(docName)) {
                return contentNode;
            }
            
            contentList = contentList.getObjectNodes(RDF.rest).next();
        }
        throw new Exception("Node not found.");
    }
    
    private Map<String, String> describeContent(GraphNode contentNode) {
        Map<String, String> item = new HashMap<String, String>();
        String docName = contentNode.getNode().toString();
        // strip < and >
        docName = docName.substring(1, docName.length()-1);
        String docText = contentNode.getLiterals(SIOC.content).next().toString();
        item.put("DOCNAME", docName);
        item.put("doc_text", docText);
        return item;
    }
    
    /* 
     * Some test data to use until we get this from the ecs
     */
    private List<Map<String,String>> getTestData() {
        List<Map<String,String>> data = new ArrayList<Map<String, String>>();
        Map<String,String> item;
        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id001"); item.put("doc_text", "aa bb cc dd ee ff gg hh ii jj aa bb cc dd ee ff gg hh ii jj");
        
        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id002"); item.put("doc_text", "aa cc dd ee gg hh ii jj aa cc dd ee gg hh ii jj");
        
        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id003"); item.put("doc_text", "aa bb dd ee ff hh ii jj aa bb dd ee ff hh ii jj");

        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id004"); item.put("doc_text", "aa bb cc ee ff hh ii jj");

        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id005"); item.put("doc_text", "aa bb cc dd ff gg ii jj");

        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id006"); item.put("doc_text", "aa bb cc dd ee gg ii jj aa bb cc dd ee gg ii jj aa bb cc dd ee gg ii jj");

        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id011"); item.put("doc_text", "kk ll mm nn oo pp qq rr ss tt kk ll mm nn oo pp qq rr ss tt kk ll mm nn oo pp qq rr ss tt");
        
        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id012"); item.put("doc_text", "kk mm nn oo qq rr ss tt");
        
        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id013"); item.put("doc_text", "kk ll nn oo pp rr ss tt");

        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id014"); item.put("doc_text", "kk ll mm oo pp rr ss tt");

        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id015"); item.put("doc_text", "kk ll mm nn pp qq ss tt");

        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id016"); item.put("doc_text", "kk ll mm nn oo qq ss tt");

        return data;
    }

    /*
     * Some test labels
     */
    private List<Map<String,String>> getTestLabels() {
        List<Map<String,String>> data = new ArrayList<Map<String, String>>();
        Map<String,String> item;
        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id001"); item.put("Label", "Positive");    
        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id002"); item.put("Label", "Positive");    
        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id003"); item.put("Label", "Positive");    
        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id004"); item.put("Label", "Positive");    

        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id011"); item.put("Label", "Negative");    

        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id012"); item.put("Label", "Negative");    

        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id013"); item.put("Label", "Negative");    

        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id014"); item.put("Label", "Negative");    
        
        return data;
    }
    
    /**
     * This method return an RdfViewable, this is an RDF serviceUri with associated
     * presentational information.
     */
    @GET
    public RdfViewable serviceEntry(@Context final UriInfo uriInfo, 
            @HeaderParam("user-agent") String userAgent) throws Exception {
//        System.out.println("BBBBBBBBBBBBBBBBBBBBBBBb");
//        System.out.println(uriInfo.getPath());
//        System.out.println(userAgent);
        //this makes sure we are nt invoked with a trailing slash which would affect
        //relative resolution of links (e.g. css)
        TrailingSlash.enforcePresent(uriInfo);
        final String resourcePath = uriInfo.getAbsolutePath().toString();
        //The URI at which this service was accessed accessed, this will be the 
        //central serviceUri in the response
        final UriRef serviceUri = new UriRef(resourcePath);
        //the in memory graph to which the triples for the response are added
        final MGraph responseGraph = new IndexedMGraph();
        //This GraphNode represents the service within our result graph
        final GraphNode node = new GraphNode(serviceUri, responseGraph);
        //The triples will be added to the first graph of the union
        //i.e. to the in-memory responseGraph
        //node.addProperty(RDF.type, Ontology.MultiEnhancer);
        //node.addProperty(RDFS.comment, new PlainLiteralImpl("A Multi Enhancer service"));
        //What we return is the GraphNode we created with a template path
        return new RdfViewable("KmxRdfProxy", node, KMXProxy.class);
    }
}
