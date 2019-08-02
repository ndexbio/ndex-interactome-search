package org.ndexbio.interactomesearch;

import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.ndexbio.interactomesearch.object.InteractomeRefNetworkEntry;
import org.ndexbio.interactomesearch.object.NetworkShortSummary;
import org.ndexbio.interactomesearch.object.SearchStatus;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.exceptions.ObjectNotFoundException;


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
  
  
 
  /*
	@POST
	@Path("/mytest")
	@Produces("application/json")
	@Consumes(MediaType.APPLICATION_JSON)

    public String mytest (@Context HttpServletRequest request) throws IOException {
	   String contentType = request.getContentType();
	   ServletInputStream in = request.getInputStream();
	   
	   BufferedReader br = new BufferedReader(new InputStreamReader(in));
	   String readLine;

	   while (((readLine = br.readLine()) != null)) {
	   System.out.println(readLine);
	   }
	   return "abc";
	}
   */
	
	@POST
	@Path("/search")
	@Produces("application/json")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response  interactomeSearch(
			final List<String> geneList
			) throws IOException, URISyntaxException, ExecutionException {
		
		//Log.getRootLogger().info("Interconnect Query term: " + queryParameters.getSearchString());

		Set<String> uppercasedGeneSet = geneList.stream().map(e -> e.toUpperCase()).collect(Collectors.toSet());
		
		UUID taskId = App.getTaskIdFromCache(uppercasedGeneSet);
		
		String url = "http://"+App.getServiceHost()  +":"+App.getPort() + "/interactome/v1/search/" + taskId + "/status";
		
	    Hashtable<String,String> result = new Hashtable<>();
	    result.put("id", taskId.toString());
		return Response.accepted().location(new URI (url)).entity(result).build();

	}

	
	@GET
	@Path("/database")
	@Produces("application/json")
	@Consumes(MediaType.APPLICATION_JSON)
	public List<InteractomeRefNetworkEntry> getDatabase() {
		
		List<InteractomeRefNetworkEntry> sources = new ArrayList<>(App.getDBTable().size()); 
	
		for ( Map.Entry<String, NetworkShortSummary> entry: App.getDBTable().entrySet()) {
			InteractomeRefNetworkEntry rec = new InteractomeRefNetworkEntry();
        	rec.setUuid(entry.getKey());
        	rec.setDescription(entry.getValue().getDescription());
        	rec.setName( entry.getValue().getName());
        	rec.setURL( entry.getValue().getURL() );
        	rec.setImageURL(entry.getValue().getImageURL());
        	sources.add(rec);
        }
		
		return sources;

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
	public SearchStatus  getSearchStatus(
			@PathParam("id")final String taskIdStr
			) throws NdexException {
		
		//Log.getRootLogger().info("Interconnect Query term: " + queryParameters.getSearchString());
		
//		java.nio.file.Path path = FileSystems.getDefault().getPath("result", taskIdStr.toString());

		SearchStatus status = App.getStatusTable().get(UUID.fromString(taskIdStr));
		if (status == null ) 
			throw new ObjectNotFoundException("Can't find task " + taskIdStr);
			
		return status;

	}
	
/*	
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
				e.printStackTrace();
				SearchStatus status = App.getStatusTable().get(taskId);
				status.setStatus(SearchStatus.failed);
				status.setMessage(e.getMessage());
				status.setProgress(100);
			} 
		}
		
	} */
	
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