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
import org.apache.clerezza.jaxrs.utils.form.FormFile;
import org.apache.clerezza.jaxrs.utils.form.MultiPartBody;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.impl.PlainLiteralImpl;
import org.apache.clerezza.rdf.ontologies.RDF;
import org.apache.clerezza.rdf.ontologies.RDFS;
import org.apache.clerezza.rdf.utils.GraphNode;
import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Property;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.Service;
import org.apache.stanbol.commons.indexedgraph.IndexedMGraph;
import org.apache.stanbol.commons.web.viewable.RdfViewable;
import org.apache.stanbol.enhancer.servicesapi.Chain;
import org.apache.stanbol.enhancer.servicesapi.ChainManager;
import org.apache.stanbol.enhancer.servicesapi.ContentItem;
import org.apache.stanbol.enhancer.servicesapi.ContentItemFactory;
import org.apache.stanbol.enhancer.servicesapi.ContentSource;
import org.apache.stanbol.enhancer.servicesapi.EnhancementException;
import org.apache.stanbol.enhancer.servicesapi.EnhancementJobManager;
import org.apache.stanbol.enhancer.servicesapi.impl.ByteArraySource;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import eu.fusepool.ecs.core.ContentStore;
import eu.fusepool.ecs.core.ContentStoreImpl;
import eu.fusepool.ecs.ontologies.ECS;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.AbstractList;
import java.util.ArrayList;
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
import org.apache.clerezza.rdf.core.Literal;
import org.apache.clerezza.rdf.core.Resource;
import org.apache.clerezza.rdf.core.Triple;
import org.apache.clerezza.rdf.core.TripleCollection;
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
@Component
@Service(Object.class)
@Property(name="javax.ws.rs", boolValue=true)
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
    private ContentItemFactory contentItemFactory;
    
    @Reference
    private EnhancementJobManager enhancementJobManager;
    
    @Reference
    private ChainManager chainManager;
    
    @Reference
    private ContentStore ecs;
    
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
     *  "items": 1000,
     *  "offset": 0,
     *  "maxFacets": 1,
     *  "labels": { "doc uri": "Positive", "doc uri2": "Negative"},
     * }
     * When fields are not provided, the shown value is the default. Execpt for
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
            throws JSONException, IOException {
        TrailingSlash.enforcePresent(uriInfo);
        log.info("ranking: " + data);
        final String resourcePath = uriInfo.getAbsolutePath().toString();
        final UriRef contentUri = new UriRef(resourcePath);
        JSONObject root = new JSONObject(data);
        
        // get original search parameters from json object
        final UriRef contentStoreUri = new UriRef(
                root.getString("contentStoreUri"));
        final UriRef contentStoreViewUri = new UriRef(
                root.getString("contentStoreViewUri"));
        final Integer items = root.has("items") ? root.getInt("items") : 10;
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
        }
        
        // redo search on ECS
        GraphNode contentStoreView = ecs.getContentStoreView(
                contentStoreUri,
                contentStoreViewUri,
                subjects,
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
        GraphNode contentList = contentStoreView.getObjectNodes(ECS.contents).next();
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
        Iterator it = id2docname.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry pair = (Map.Entry) it.next();
            docname2id.put((String)pair.getValue(), (Integer)pair.getKey());
            System.out.println((String)pair.getValue());
        }
            
        String stoplist = kmxClient.createStoplist().getString("stoplist_name");
        ArrayList<String> words = new ArrayList<String>();
        words.add("a");
        words.add("about");
        words.add("after");
        words.add("all");
        words.add("also");
        words.add("an");
        words.add("and");
        words.add("any");
        words.add("are");
        words.add("as");
        words.add("at");
        words.add("be");
        words.add("because");
        words.add("been");
        words.add("but");
        words.add("by");
        words.add("can");
        words.add("co");
        words.add("corp");
        words.add("could");
        words.add("for");
        words.add("from");
        words.add("had");
        words.add("has");
        words.add("have");
        words.add("he");
        words.add("her");
        words.add("his");
        words.add("if");
        words.add("in");
        words.add("inc");
        words.add("into");
        words.add("is");
        words.add("it");
        words.add("its");
        words.add("last");
        words.add("more");
        words.add("most");
        words.add("mr");
        words.add("mrs");
        words.add("ms");
        words.add("mz");
        words.add("no");
        words.add("not");
        words.add("of");
        words.add("on");
        words.add("one");
        words.add("only");
        words.add("or");
        words.add("other");
        words.add("out");
        words.add("over");
        words.add("s");
        words.add("says");
        words.add("she");
        words.add("so");
        words.add("some");
        words.add("such");
        words.add("than");
        words.add("that");
        words.add("the");
        words.add("their");
        words.add("there");
        words.add("they");
        words.add("this");
        words.add("to");
        words.add("up");
        words.add("was");
        words.add("we");
        words.add("were");
        words.add("when");
        words.add("which");
        words.add("who");
        words.add("will");
        words.add("with");
        words.add("would");

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
        
        while (rkeys.hasNext()) {
            // TODO: make sure HypCat1 corrosponds to Positive
            String docId = (String) rkeys.next();
            JSONObject jsonProb = response.getJSONObject(docId);
            Integer docIdInt = Integer.parseInt(docId);
            String docName = id2docname.get(docIdInt);
            Double posProb = jsonProb.getDouble("Prob1");
//            System.out.println(docName + ":" + posProb);
            rankedDocnames.put(posProb, docName);
        }
        
        Iterator it2 = rankedDocnames.entrySet().iterator();
        while (it2.hasNext()) {
            Map.Entry pairs = (Map.Entry) it2.next();
            System.out.println(pairs.getKey() + ": " + pairs.getValue());
        }
        
        // TODO: reorder the result graph and remove the sioc content
        // either sort this list:
        //contentStoreView.getObjectNodes(ECS.contents).next().asList();
        // OR make a new one
//        BNode myNewListResource = new BNode();
//        contentStoreView.addProperty(ECS.contents, myNewListResource);
//        List<Resource> rdfList = new RdfList(myNewListResource);
//        rdfList.add(theFirstContentGraphNode.getNode());
//        rdfList.add(theSecondContentGraphNode.getNode());

        return new RdfViewable("ContentStoreView", contentStoreView, ContentStoreImpl.class);
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
        System.out.println("BBBBBBBBBBBBBBBBBBBBBBBb");
        System.out.println(uriInfo.getPath());
        System.out.println(userAgent);
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
        return new RdfViewable("MultiEnhancer", node, KMXProxy.class);
    }
    
    /**
     * A method to experiment with the full content from ECS
     */
    @GET
    @Path("content")
    public RdfViewable Experiment(@Context final UriInfo uriInfo) {
        List<String> searchs = new ArrayList<String>();
        List<UriRef> subjects = new ArrayList<UriRef>();
        subjects.add(new UriRef(""));
        searchs.add("");
        GraphNode result = ecs.getContentStoreView(
            new UriRef("http://beta.fusepool.com/ecs/"),
            new UriRef("http://beta.fusepool.com/ecs/?search=" + searchs.get(0)),
            new ArrayList<UriRef>(),
            searchs,
            10,
            0,
            10,
            true);

        log.info("nr results: " + result.asList().size());
        
        StringBuilder sb = new StringBuilder();
        Iterator<Resource> valuesIter = result.getObjects(SIOC.content);
        while (valuesIter.hasNext()) {
            final Resource value = valuesIter.next();
            sb.append(value.toString());
            log.info("res " + value.toString());
            System.err.println(value.toString());
        }
        
        //return sb.toString();
        result.deleteProperties(SIOC.content);
        return new RdfViewable("ContentStoreView", result, ContentStoreImpl.class);
    }
}
