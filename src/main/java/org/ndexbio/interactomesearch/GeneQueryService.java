package org.ndexbio.interactomesearch;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.Hashtable;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.util.log.Log;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.interactomesearch.object.NetworkShortSummary;
import org.ndexbio.interactomesearch.object.SearchStatus;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.NdexPropertyValuePair;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.rest.client.NdexRestClient;
import org.ndexbio.rest.client.NdexRestClientModelAccessLayer;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.cache.RemovalListener;
import com.google.common.cache.RemovalNotification;

public class GeneQueryService {
	
	private static int resultCacheSize = 600;

	private static GeneSymbolIndexer geneSearcher;

	private static final Hashtable<String, NetworkShortSummary> dbTable = new Hashtable<>();

	private static final Hashtable<UUID, SearchStatus> statusTable = new Hashtable<>();

	private static RemovalListener<Set<String>, UUID> removalListener = new RemovalListener<Set<String>, UUID>() {
		@Override
		public void onRemoval(RemovalNotification<Set<String>, UUID> removal) {
			UUID taskId = removal.getValue();
			App.getStatusTable().remove(taskId);
			// remove the directory from file system.
			File resultDir = new File(App.getWorkingPath() + "/result/" + taskId.toString());
			try {
				FileUtils.deleteDirectory(resultDir);
			} catch (IOException e) {
				Log.getRootLogger().warn("Failed to remove result director for " + taskId.toString());
				e.printStackTrace();

			}
		}
	};
	 
	 // gene set to taskID cache
	private static final LoadingCache<Set<String>, UUID> geneSetSearchCache = CacheBuilder.newBuilder()
			.initialCapacity(resultCacheSize).maximumSize(resultCacheSize).removalListener(removalListener)
			.build(new CacheLoader<Set<String>, UUID>() {
				@Override
				public UUID load(Set<String> geneSet) throws IOException {
					UUID taskId = UUID.nameUUIDFromBytes(geneSet.stream().collect(Collectors.joining(",")).getBytes());
					java.nio.file.Path path = Paths.get(App.getWorkingPath() + "/result/" + taskId.toString());
					Files.createDirectories(path);
					// add entry to the status table
					SearchStatus st = new SearchStatus();
					st.setStatus(SearchStatus.submitted);
					App.getStatusTable().put(taskId, st);

					SearchWorkerThread t = new SearchWorkerThread(geneSet, taskId, st);
					t.start();

					return taskId;
				}
			});

	public GeneQueryService(String workingPath, String type, String ndexServerName)
			throws SQLException, JsonParseException, JsonMappingException, IOException, NdexException {

		geneSearcher = new GeneSymbolIndexer(workingPath + "/genedb", type);

		NdexRestClientModelAccessLayer ndex = new NdexRestClientModelAccessLayer(
				new NdexRestClient(null, null, ndexServerName));

		for (NetworkShortSummary summary : geneSearcher.getIdMapper().values()) {
			NetworkSummary sum = ndex.getNetworkSummaryById(UUID.fromString(summary.getUuid()));
			summary.setDescription(sum.getDescription());
			summary.setEdgeCount(sum.getEdgeCount());
			summary.setName(sum.getName());
			summary.setNodeCount(sum.getNodeCount());
			NdexPropertyValuePair iconURLProp = sum.getPropertyByName("__iconurl");
			if (iconURLProp != null)
				summary.setImageURL(iconURLProp.getValue());
			NdexPropertyValuePair networkType = sum.getPropertyByName("networkType");
			String listofstr = ATTRIBUTE_DATA_TYPE.LIST_OF_STRING.toString();
			if (summary.getType() == null && networkType != null && networkType.getDataType().equals(listofstr)) {
				ObjectMapper mapper = new ObjectMapper();
				String[] netTypes = mapper.readValue(networkType.getValue(), String[].class);
				for (String s : netTypes) {
					if (s.equals("ppi") || s.equals("pathway")) {
						summary.setType("i");
						break;
					} else if (s.equals("geneassociation") || s.equals("proteinassociation")) {
						summary.setType("a");
						break;
					}
				}
			}

			summary.setURL(ndexServerName + "/network/" + sum.getExternalId());
			dbTable.put(summary.getUuid(), summary);
		}
	}

}
