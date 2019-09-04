package org.ndexbio.interactomesearch;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import org.ndexbio.interactomesearch.object.SearchStatus;
import org.ndexbio.model.exceptions.NdexException;

public class SearchWorkerThread extends Thread {
	private UUID taskId;
	private Set<String> geneList;
	private SearchStatus status;
	private NetworkQueryManager queryManager;
	
	public SearchWorkerThread (Set<String> geneList, UUID  taskUUID, SearchStatus statusHolder , NetworkQueryManager queryManager) {
		taskId = taskUUID;
		this.geneList = geneList;
		this.status = statusHolder;
		this.queryManager = queryManager;
	}
	
	@Override
	public void run() {
		try {
			
			queryManager.search(geneList, taskId);
		} catch ( SQLException | IOException | NdexException e) {
			e.printStackTrace();
			status.setStatus(SearchStatus.failed);
			status.setMessage(e.getMessage());
			status.setProgress(100);
		} 
	}
	
}
