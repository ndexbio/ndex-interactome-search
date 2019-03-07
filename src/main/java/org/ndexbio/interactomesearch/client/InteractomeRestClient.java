package org.ndexbio.interactomesearch.client;

import java.io.InputStream;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericType;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.ndexbio.interactomesearch.object.InteractomeRefNetworkEntry;
import org.ndexbio.interactomesearch.object.InteractomeSearchResult;
import org.ndexbio.interactomesearch.object.SearchStatus;
import org.ndexbio.model.errorcodes.NDExError;
import org.ndexbio.model.exceptions.NdexException;

public class InteractomeRestClient {
	 private String _restEndPoint;
	 private String _userAgent = "NDEx-InteractomeClient/0.1.0";
	 	 
	    /**
	     * 
	     * @param restEndPoint Full prefix of all endpoints of the service. Example: "http://localhost:8285/interactome/v1/
	     * @param userAgent
	     */
	    public InteractomeRestClient(final String restEndPoint, final String userAgent) {
	        if (userAgent != null){
	            _userAgent = _userAgent + " " + userAgent;
	        }
	        if (restEndPoint == null){
	            throw new IllegalArgumentException("restEndPoint cannot be null");
	        }
	        _restEndPoint = restEndPoint;
	    }
	    
	    public List<InteractomeRefNetworkEntry> getDatabase() throws NdexException {
			Client client = ClientBuilder.newBuilder().build();
	        WebTarget target = client.target(_restEndPoint  + "database");
	        Response response = target.request(MediaType.APPLICATION_JSON).get();
	        
	        if ( response.getStatus()!=200) {
	        	NDExError obj = response.readEntity(NDExError.class);
	        		throw new NdexException(obj.getMessage());
	        }
	        
			List<InteractomeRefNetworkEntry> in = response.readEntity(new GenericType<List<InteractomeRefNetworkEntry>>() {/**/});
			return in;
	    }
	    
	    
	    public UUID search (List<String> genes) throws NdexException {
	    	Client client = ClientBuilder.newBuilder().build();
	        WebTarget target = client.target(_restEndPoint  + "search");
	        Response response = target.request().post(Entity.entity(genes, MediaType.APPLICATION_JSON));

	        if ( response.getStatus()!=202) {
	        	NDExError obj = response.readEntity(NDExError.class);
	        		throw new NdexException(obj.getMessage());
	        }
	        
			Hashtable<String,String> in = response.readEntity(new GenericType<Hashtable<String,String>>() {/**/});
			return UUID.fromString(in.get("id"));

	    }
	    
	    public SearchStatus getSearchStatus (UUID taskId) throws NdexException {
	    	Client client = ClientBuilder.newBuilder().build();
	        WebTarget target = client.target(_restEndPoint  + "search/" + taskId + "/status" );
	        Response response = target.request().get();

	        if ( response.getStatus()!=200) {
	        	NDExError obj = response.readEntity(NDExError.class);
	        		throw new NdexException(obj.getMessage());
	        }
	        
			SearchStatus in = response.readEntity(SearchStatus.class);
			return in;

	    }

	    public List<InteractomeSearchResult> getSearchResult (UUID taskId) throws NdexException {
	    	Client client = ClientBuilder.newBuilder().build();
	        WebTarget target = client.target(_restEndPoint  + "search/" + taskId );
	        Response response = target.request().get();

	        if ( response.getStatus()!=200) {
	        	NDExError obj = response.readEntity(NDExError.class);
	        		throw new NdexException(obj.getMessage());
	        }
	        
	        List<InteractomeSearchResult> in = response.readEntity(new GenericType<List<InteractomeSearchResult>>() {/**/});
			return in;

	    }
	    
	    public InputStream getOverlayedNetworkStream (UUID taskId, UUID networkId) throws NdexException {
	    	Client client = ClientBuilder.newBuilder().build();
	        WebTarget target = client.target(_restEndPoint  + "search/" + taskId + "/overlaynetwork?networkUUID=" + networkId );
	        Response response = target.request().get();

	        if ( response.getStatus()!=200) {
	        	NDExError obj = response.readEntity(NDExError.class);
	        		throw new NdexException(obj.getMessage());
	        }
	        
	        return response.readEntity(InputStream.class);

	    }
}
