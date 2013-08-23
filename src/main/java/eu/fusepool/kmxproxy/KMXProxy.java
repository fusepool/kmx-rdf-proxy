package eu.fusepool.kmxproxy;

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
import java.util.ArrayList;
import java.util.List;
import org.codehaus.jettison.json.JSONArray;

/**
 * Manages communication with a KMX service and transformation of responses
 */
@Component
@Service(Object.class)
@Property(name="javax.ws.rs", boolValue=true)
@Path("kmxrdfproxy")
public class KMXProxy {
    
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
    
    @Activate
    protected void activate(ComponentContext context) {
        log.info("The kmx proxy service is being activated");
    }
    
    @Deactivate
    protected void deactivate(ComponentContext context) {
        log.info("The kmx proxy service is being deactivated");
    }
    
    @POST
    @Path("ranking")
    public String ranking(@Context final UriInfo uriInfo, final String data)
            throws JSONException {
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
        
//        System.out.println(contentStoreUri);
//        System.out.println(contentStoreViewUri);
//        System.out.println(items);
//        System.out.println(offset);
//        System.out.println(maxFacets);
//        System.out.println(searchs);
//        System.out.println(subjects);
        // redo search on ECS
        GraphNode result = ecs.getContentStoreView(
                contentStoreUri,
                contentStoreViewUri,
                subjects,
                searchs,
                items,
                offset,
                maxFacets,
                true);
        
        // get results
        
        // upload content to a new workspace
        // set training docs
        // make model
        // apply model
        // return results
        return result.toString();
//        return "result";
    }
    
    /**
     * This method return an RdfViewable, this is an RDF serviceUri with associated
     * presentational information.
     */
    @GET
    public RdfViewable serviceEntry(@Context final UriInfo uriInfo, 
            @HeaderParam("user-agent") String userAgent) throws Exception {
        log.info("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
        System.out.println("BBBBBBBBBBBBBBBBBBBBBBBb");
        System.out.println(uriInfo.getPath());
        System.out.println(userAgent);
        //this maks sure we are nt invoked with a trailing slash which would affect
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
