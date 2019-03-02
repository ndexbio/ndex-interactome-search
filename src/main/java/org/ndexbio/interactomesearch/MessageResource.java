package org.ndexbio.interactomesearch;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.rest.client.NdexRestClient;
import org.ndexbio.rest.client.NdexRestClientModelAccessLayer;

import com.fasterxml.jackson.core.JsonProcessingException;


@Path("/v1")
public class MessageResource {
	

	@SuppressWarnings("static-method")
	@GET
	@Path("/status")
	@Produces("application/json")
	public Map<String,String> printMessage() {
		Map<String,String> result = new HashMap<>();
		result.put("status", "online");
		return result;
	}
  
  
 
  
	@POST
	@Path("/mytest")
	@Produces("application/json")
	@Consumes(MediaType.APPLICATION_JSON)

    public String mytest (@Context HttpServletRequest request/*, InputStream in*/) throws IOException {
	   String contentType = request.getContentType();
	   ServletInputStream in = request.getInputStream();
	   
	   BufferedReader br = new BufferedReader(new InputStreamReader(in));
	   String readLine;

	   while (((readLine = br.readLine()) != null)) {
	   System.out.println(readLine);
	   }
	   return "abc";
	}
  
	
	@POST
	@Path("/search")
	@Produces("application/json")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response  interactomeSearch(
			final List<String> geneList
			) throws IOException, URISyntaxException {
		
		//Log.getRootLogger().info("Interconnect Query term: " + queryParameters.getSearchString());

		UUID taskId = UUID.nameUUIDFromBytes(geneList.stream().
				collect(Collectors.joining(",")).getBytes());
		
		java.nio.file.Path path =  Paths.get(App.getWorkingPath() + "/result/" + taskId.toString());

		if (createDirIfNotExists(path)) {
		  // add entry to the status table
		  SearchStatus st = new SearchStatus();
		  App.getStatusTable().put(taskId, st);
		  st.setStatus(SearchStatus.submitted);
		  
		  //kick start the search.	
		  SearchWorkerThread t = new SearchWorkerThread(geneList, taskId);
		  t.start();
		}	
		String url = "http://"+App.getServiceHost()  +":"+App.getPort() + "/interactome/v1/search/" + taskId + "/status";
		
	    Hashtable<String,String> result = new Hashtable<>();
	    result.put("id", taskId.toString());
		return Response.accepted().location(new URI (url)).entity(result).build();

	}

	
	@GET
	@Path("/database")
	@Produces("application/json")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response getDatabase() throws NdexException, JsonProcessingException, IOException {
		
		List<Map<String, String>> sources = new ArrayList<>(App.getDBTable().size()); 
	
		for ( Map.Entry<String, NetworkShortSummary> entry: App.getDBTable().entrySet()) {
        	HashMap<String,String> rec = new HashMap<>();
        	rec.put("uuid", entry.getKey());
        	rec.put("description",entry.getValue().getDescription());
        	rec.put("name", entry.getValue().getName());
        	rec.put("url", entry.getValue().getURL() );
        	sources.add(rec);
        }
		
		return Response.ok(sources).build();

	}
	
	
	@GET
	@Path("/search/{id}")
	@Produces("application/json")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response  getSearchResult(
			@PathParam("id")final String taskIdStr
			) throws NdexException {
		
		//Log.getRootLogger().info("Interconnect Query term: " + queryParameters.getSearchString());
		
//		java.nio.file.Path path = FileSystems.getDefault().getPath("result", taskIdStr.toString());

		SearchStatus status = App.getStatusTable().get(UUID.fromString(taskIdStr));
		if (status == null ) 
			throw new ObjectNotFoundException("Can't find task " + taskIdStr);
		
		if ( status.getStatus().equals(SearchStatus.complete)) {
			String resultFilePath = App.getWorkingPath() + "/result/" + taskIdStr + "/result";

	    	try {
				FileInputStream in = new FileInputStream(resultFilePath)  ;
			
			//	setZipFlag();
				ResponseBuilder r = Response.ok();
				return r.type(MediaType.APPLICATION_JSON_TYPE).entity(in).build();
			} catch (IOException e) {
				throw new NdexException ("Interactome service can't find file: " + e.getMessage());
			}
			
		} 
		 throw new ObjectNotFoundException("This search has no result ready. Search status: " + status.getStatus());

	}
	
	
	@GET
	@Path("/search/{id}/status")
	@Produces("application/json")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response  getSearchStatus(
			@PathParam("id")final String taskIdStr
			) throws NdexException {
		
		//Log.getRootLogger().info("Interconnect Query term: " + queryParameters.getSearchString());
		
//		java.nio.file.Path path = FileSystems.getDefault().getPath("result", taskIdStr.toString());

		SearchStatus status = App.getStatusTable().get(UUID.fromString(taskIdStr));
		if (status == null ) 
			throw new ObjectNotFoundException("Can't find task " + taskIdStr);
			
		return Response.ok(status).build();

	}
	
	
	private class SearchWorkerThread extends Thread {
		private UUID taskId;
		private List<String> geneList;
		
		public SearchWorkerThread (List<String> geneList, UUID  taskUUID ) {
			taskId = taskUUID;
			this.geneList = geneList;
		}
		
		@Override
		public void run() {
			try {
				NetworkQueryManager b = new NetworkQueryManager();
				b.search(geneList, taskId);
			} catch ( SQLException | IOException | NdexException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				SearchStatus status = App.getStatusTable().get(taskId);
				status.setStatus(SearchStatus.failed);
				status.setMessage(e.getMessage());
				status.setProgress(100);
			} 
		}
		
	}
	
	@GET
	@Path("/search/{taskId}/overlaynetwork")
	@Produces("application/json")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response  getOverlaidNetwork(
				@PathParam("taskId") final String taskIdStr,
				@QueryParam("networkUUID") String networkIdStr
				) throws NdexException {
			
			UUID networkId = UUID.fromString(networkIdStr);

			//Set<Long> nodeIds = new TreeSet<>();
	 		
		//	java.nio.file.Path path = FileSystems.getDefault().getPath("result", taskIdStr);

	 		
	 		String cxFilePath = App.getWorkingPath() + "/result/" + taskIdStr + "/" + networkId + ".cx";

	    	try {
				FileInputStream in = new FileInputStream(cxFilePath)  ;
			
			//	setZipFlag();
				ResponseBuilder r = Response.ok();
				return r.type(MediaType.APPLICATION_JSON_TYPE).entity(in).build();
			} catch (IOException e) {
				throw new NdexException ("Interactome service can't find file: " + e.getMessage());
			}
			
		}
		
	/*	private class CXNetworkQueryWriterThread extends Thread {
			private OutputStream o;
			private UUID networkId;
			private SimplePathQuery parameters;
			private Set<Long> startingNodeIds;
			
			public CXNetworkQueryWriterThread (OutputStream out, UUID  networkUUID, SimplePathQuery query,Set<Long> nodeIds ) {
				o = out;
				networkId = networkUUID;
				this.parameters = query;
				startingNodeIds = nodeIds;
			}
			
			@Override
			public void run() {
				NetworkQueryManager b = new NetworkQueryManager(networkId, parameters);
				try {
					b.neighbourhoodQuery(o, startingNodeIds);
				} catch (IOException e) {
					e.printStackTrace();
				} finally {
					try {
						o.flush();
						o.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			
		} */
		
	/**
	 *  	
	 * @param path
	 * @return true if a new directory was created. false if the directory already exists.
	 * @throws IOException
	 */
		
	private synchronized static boolean createDirIfNotExists(java.nio.file.Path path) throws IOException {
		if (Files.exists(path)) 
			return false;

		Files.createDirectories(path);
		return true;
	}
	  
}	