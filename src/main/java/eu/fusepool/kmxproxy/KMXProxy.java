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
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import org.apache.clerezza.rdf.core.Literal;
import org.apache.clerezza.rdf.core.Resource;
import org.apache.clerezza.rdf.ontologies.SIOC;
import org.codehaus.jettison.json.JSONArray;


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
    
    @POST
    @Path("ranking")
    public String ranking(@Context final UriInfo uriInfo, final String data)
            throws JSONException, IOException {
        TrailingSlash.enforcePresent(uriInfo);
        final String resourcePath = uriInfo.getAbsolutePath().toString();
        final UriRef contentUri = new UriRef(resourcePath);
        JSONObject root = new JSONObject(data);
        
        // get original search parameters from json object
        final UriRef contentStoreUri = new UriRef(
                root.getString("contentStoreUri"));
        final UriRef contentStoreViewUri = new UriRef(
                root.getString("contentStoreViewUri"));
        final Integer items = root.getInt("items");
        final Integer offset = root.getInt("offset");
        final Integer maxFacets = root.getInt("maxFacets");
        final JSONArray jsonArraySearchs = root.getJSONArray("subjects");
        final List<String> searchs = new ArrayList<String>();
        for (int i=0; i<jsonArraySearchs.length(); i++) {
            searchs.add( jsonArraySearchs.getString(i) );
        }
        final JSONArray jsonArraySubjects = root.getJSONArray("subjects");
        final List<UriRef> subjects = new ArrayList<UriRef>();
        for (int i=0; i<jsonArraySubjects.length(); i++) {
            subjects.add( new UriRef(jsonArraySubjects.getString(i)));
        }
        // TODO get labels
        
        // redo search on ECS
        GraphNode result = ecs.getContentStoreView(
                contentStoreUri,
                contentStoreViewUri,
                subjects,
                searchs,
                items,
                offset,
                maxFacets,
                false);
        // get results
//        Iterator<Resource> valuesIter = result.getObjects(SIOC.content);
//        while (valuesIter.hasNext()) {
//            final Resource value = valuesIter.next();
//        }
        // for now use the test data
        List<Map<String,String>> documents = getTestData();
        List<Map<String,String>> labels = getTestLabels();


        // upload content to a new workspace
        JSONObject response = kmxClient.createDataset(documents.get(0).keySet());
        Integer dataset_id = (Integer) response.get("dataset_id");
        for (Map<String,String> doc: documents) {
            kmxClient.addItemToDataset(dataset_id, doc);
        }
        response = kmxClient.createWorkspace();
        Integer workspace_id = (Integer) response.get("object_id");
        HashMap<String, Integer> features = new HashMap<String, Integer>();
        features.put("DOCNAME", 0);
        features.put("doc_text", 1);
        response = kmxClient.addDatasetToWorkspace(workspace_id, dataset_id, features);
        
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
            it.remove(); // avoids a ConcurrentModificationException
        }
            
        String stoplist = kmxClient.createStoplist().getString("stoplist_name");
        ArrayList<String> words = new ArrayList<String>();
        words.add("a");
        words.add("about");
        words.add("after");
        // TODO add more
        kmxClient.AddWordsToStoplist(stoplist, words);
        
        // set training docs
        HashMap<String, String> labelMap = new HashMap<String, String>();
        for (Map<String,String> item: labels) {
            String docname = item.get("DOCNAME");
            String label = item.get("Label");
            Integer id = docname2id.get(docname);
            labelMap.put(id.toString(), label);
            System.out.println(id.toString() + " " + label);
        }
        
        kmxClient.labelWorkspaceItems(workspace_id, labelMap);
                
        // make model
        HashMap<String, String> settingsMap = new HashMap<String, String>();
        settingsMap.put("STOPLIST", stoplist);
        settingsMap.put("MAX_FEATURES", "750");
        Integer model_id = kmxClient.createSVMModel(workspace_id, settingsMap).getInt("object_id");
        
        // apply model
        response = kmxClient.applySVMModelToWorkspace(model_id, workspace_id);
        
        
        return response.toString();
        //return result.toString();
//        return "result";
        //return new RdfViewable("KMXProxy", result, KMXProxy.class);
    }
    
    /* 
     * Some test data to use until we get this from the ecs
     */
    private List<Map<String,String>> getTestData() {
        List<Map<String,String>> data = new ArrayList<Map<String, String>>();
        Map<String,String> item;
        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id001"); item.put("doc_text", "aa bb cc dd ee ff gg hh ii jj");
        
        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id002"); item.put("doc_text", "aa cc dd ee gg hh ii jj");
        
        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id003"); item.put("doc_text", "aa bb dd ee ff hh ii jj");

        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id004"); item.put("doc_text", "aa bb cc ee ff hh ii jj");

        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id005"); item.put("doc_text", "aa bb cc dd ff gg ii jj");

        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id006"); item.put("doc_text", "aa bb cc dd ee gg ii jj");

        item = new HashMap<String, String>();
        data.add(item);
        item.put("DOCNAME", "id011"); item.put("doc_text", "kk ll mm nn oo pp qq rr ss tt");
        
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
}
