package org.ndexbio.interactomesearch;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.CyVisualPropertiesElement;
import org.ndexbio.cxio.aspects.datamodels.EdgeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.EdgesElement;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.cxio.core.AspectIterator;
import org.ndexbio.cxio.core.NdexCXNetworkWriter;
import org.ndexbio.cxio.metadata.MetaDataCollection;
import org.ndexbio.cxio.metadata.MetaDataElement;
import org.ndexbio.interactomesearch.object.GeneQueryNodes;
import org.ndexbio.interactomesearch.object.GeneSymbolSearchResult;
import org.ndexbio.interactomesearch.object.InteractomeSearchResult;
import org.ndexbio.interactomesearch.object.NetworkShortSummary;
import org.ndexbio.interactomesearch.object.SearchStatus;
import org.ndexbio.model.cx.CitationElement;
import org.ndexbio.model.cx.EdgeCitationLinksElement;
import org.ndexbio.model.cx.EdgeSupportLinksElement;
import org.ndexbio.model.cx.FunctionTermElement;
import org.ndexbio.model.cx.NamespacesElement;
import org.ndexbio.model.cx.NodeCitationLinksElement;
import org.ndexbio.model.cx.NodeSupportLinksElement;
import org.ndexbio.model.cx.SupportElement;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.tools.NodeDegreeHelper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

public class NetworkQueryManager {

	static Logger accLogger = Log.getRootLogger();
//  	Log.getRootLogger().info("Embedded Jetty logging started.", new Object[]{});

//	private int depth;
//	private String netId;
	private static final Long consistencyGrp = Long.valueOf(1L);
	private static final String mdeVer = "1.0";
	private static final String PROGRESS = "progress";
	
	private static final int edgeLimit = 500;
	
	private static String pathPrefix = "/opt/ndex/data/";
/*	private boolean usingOldVisualPropertyAspect;
	private boolean errorOverLimit;
	private boolean directOnly;
	private String searchTerms; */
	private GeneSymbolIndexer symbolDB;
	
	public enum PPIQueryType {Direct, Interconnect, Neighborhood, Adjacent} 
	
	//private static String fsPath;
	
	public NetworkQueryManager () {
		
	   symbolDB = App.getGeneSearcher();
	}
	
	public static void setDataFilePathPrefix(String serverFileRepoPrefix) {
		pathPrefix = serverFileRepoPrefix;
	}
	
	public static String getDataFilePathPrefix() { return pathPrefix; }
	
	//public static String getFsPath() { return fsPath;}
	
	//public static void setFsPath(String workingDir) {fsPath = workingDir;}
	
	public void search(List<String> genes, UUID taskId) throws SQLException, IOException, NdexException {

		long t1 = Calendar.getInstance().getTimeInMillis();

		GeneSymbolSearchResult r = symbolDB.search(genes);
		
		//new File( fsPath + taskId + "/_tmp").mkdirs();
		
		SearchStatus st = App.getStatusTable().get(taskId);
		st.setStatus(SearchStatus.processing);
        st.setNumberOfHits(r.getResultSet().size());
        st.setHitGenes(r.getHitGenes());
        st.setQuery(genes);

        float counter = 0 ;
        int total = r.getResultSet().size();
        
        List<InteractomeSearchResult> resultList = new ArrayList<>(total);
        
        for (Map.Entry<Integer, GeneQueryNodes> e: r.getResultSet().entrySet()) {

        	NetworkShortSummary summary = symbolDB.getShortSummaryFromNetId(e.getKey());
			String netUUIDStr = summary.getUuid();
	
			if (netUUIDStr == null)
				throw new NdexException("Network id "+ e.getKey() + " is not found in UUID mapping table." );

			Hashtable<String,Object> status = new Hashtable<>();
			status.put("status",  SearchStatus.processing);
			status.put(PROGRESS, 0);
			st.getSources().put(netUUIDStr,status);

			if ( e.getValue().getNodes().size() > 0) {
			//neighbourhoodQuery(taskId, e.getKey(), e.getValue().getNodes(), genes);
				resultList.add(runQuery(taskId, summary, e.getValue().getNodes(), genes, status, e.getValue().getHitGenes()));
			}
			//update the status record	
			status.put(PROGRESS, 100);
			status.put("status",  SearchStatus.complete);
			counter += 1.0;
	        st.setProgress(Math.round(counter*100/total));
		}
        
        //sort the results and then write out to file.
        
        Collections.sort(resultList, new Comparator<InteractomeSearchResult>() {
            @Override
            public int compare(InteractomeSearchResult h1, InteractomeSearchResult h2) {
            	
            	// sorting by score. value = edgeCount*3 + nodeCount
            	int s1 = h1.getEdgeCount() *3 + h2.getNodeCount();
            	int s2 = h2.getEdgeCount() *3 + h2.getNodeCount();
            	
            	
            	if (s1>s2) return -1;
            	if (s1 < s2 ) return 1;
          /*  	if (s1 == s2) {
           		if (h1.getSummary().getParentEdgeCount() > h2.getSummary().getParentEdgeCount() ) 
            			return -1;
            		if (h1.getSummary().getParentEdgeCount() < h2.getSummary().getParentEdgeCount() ) 
            		    return 1;
            	}*/
        		return 0;            	
            }
        });
        
        int i = 1 ;
        for (InteractomeSearchResult ele : resultList ) {
        	ele.setRank(i++);
        }
        
		String resultFileName = App.getWorkingPath() + "/result/" + taskId.toString() + "/result";
		ObjectMapper mapper = new ObjectMapper();
		ObjectWriter writer = mapper.writer(new DefaultPrettyPrinter());
		writer.writeValue(new File(resultFileName), resultList);	
		
		st.setWallTime(Calendar.getInstance().getTimeInMillis() - t1);
		st.setProgress(100);
		st.setStatus(SearchStatus.complete);
	}
		
	/**
	 * 
	 * @param taskId
	 * @param netId
	 * @param nodeIds
	 * @param genes
	 * @throws IOException
	 * @throws NdexException
	 */
	// comment out for now, but don't remove this. We might need this as an alternative search function in the future.
/*	private void neighbourhoodQuery(UUID taskId, String netUUIDStr, final Set<Long> nodeIds, Collection<String> genes) throws IOException, NdexException {
		long t1 = Calendar.getInstance().getTimeInMillis();
		Set<Long> edgeIds = new TreeSet<> ();
		
		String tmpFileName = App.getWorkingPath() + "/result/" + taskId.toString() + "/tmp_" + netUUIDStr;
		
		Set<Long> newNodeIds = new TreeSet<> ();

		try (FileOutputStream out = new FileOutputStream (tmpFileName) ) {
		
			NdexCXNetworkWriter writer = new NdexCXNetworkWriter(out, true);
			MetaDataCollection md = prepareMetadata(netUUIDStr) ;
			writer.start();
			writer.writeMetadata(md);
		
			MetaDataCollection postmd = new MetaDataCollection();
		
			writeContextAspect(netUUIDStr, writer, md, postmd);

			//int cnt = 0;
	
			if (md.getMetaDataElement(EdgesElement.ASPECT_NAME) != null) {
				writer.startAspectFragment(EdgesElement.ASPECT_NAME);
				writer.openFragment();
			
				try (AspectIterator<EdgesElement> ei = new AspectIterator<>( netUUIDStr,EdgesElement.ASPECT_NAME, EdgesElement.class, pathPrefix)) {
					while (ei.hasNext()) {
						EdgesElement edge = ei.next();
							if (nodeIds.contains(edge.getSource())
									|| nodeIds.contains(edge.getTarget())) {
//								cnt ++;
								writer.writeElement(edge);
								edgeIds.add(edge.getId());
								if (!nodeIds.contains(edge.getSource()))
									newNodeIds.add(edge.getSource());
								if ( !nodeIds.contains(edge.getTarget()))
									newNodeIds.add(edge.getTarget());
							}

					}
				}

				newNodeIds.addAll(nodeIds);
				
				accLogger.info("Query returned " + writer.getFragmentLength() + " edges.");
				writer.closeFragment();
				writer.endAspectFragment();
			
			}
		
			accLogger.info ( "done writing out edges.");
			
			//write nodes
			writer.startAspectFragment(NodesElement.ASPECT_NAME);
			writer.openFragment();
			try (AspectIterator<NodesElement> ei = new AspectIterator<>(netUUIDStr, NodesElement.ASPECT_NAME, NodesElement.class, pathPrefix)) {
				while (ei.hasNext()) {
					NodesElement node = ei.next();
					if (newNodeIds.contains(Long.valueOf(node.getId()))) {
						writer.writeElement(node);
					}
				}
			}
			writer.closeFragment();
			writer.endAspectFragment();
			if ( newNodeIds.size()>0) {
				MetaDataElement mde1 = new MetaDataElement(NodesElement.ASPECT_NAME,mdeVer);
				mde1.setElementCount((long)newNodeIds.size());
				mde1.setIdCounter(newNodeIds.isEmpty()? 0L : Collections.max(newNodeIds));
				postmd.add(mde1);
			}
		
			//output the full neighborhood.
			if (md.getMetaDataElement(EdgesElement.ASPECT_NAME) != null) {
				writer.startAspectFragment(EdgesElement.ASPECT_NAME);
				writer.openFragment();
			
				try (AspectIterator<EdgesElement> ei = new AspectIterator<>( netUUIDStr,EdgesElement.ASPECT_NAME, EdgesElement.class, pathPrefix)) {
					while (ei.hasNext()) {
						EdgesElement edge = ei.next();
						if ( (!edgeIds.contains(edge.getId())) && newNodeIds.contains(edge.getSource())
								&& newNodeIds.contains(edge.getTarget())) {
							//cnt ++;
							writer.writeElement(edge);
							edgeIds.add(edge.getId());
						}
					}
				}
				writer.closeFragment();
				writer.endAspectFragment();
			}
		
			if  (edgeIds.size()>0) {
				MetaDataElement mde = new MetaDataElement(EdgesElement.ASPECT_NAME,mdeVer);
				mde.setElementCount((long)edgeIds.size());
				mde.setIdCounter(edgeIds.isEmpty()? 0L : Collections.max(edgeIds));
				postmd.add(mde);
			}

//			String queryName =  "" ;
			ArrayList<NetworkAttributesElement> provenanceRecords = new ArrayList<> (2);
			provenanceRecords.add(new NetworkAttributesElement (null, "prov:wasDerivedFrom", netUUIDStr));
			provenanceRecords.add(new NetworkAttributesElement (null, "prov:wasGeneratedBy",
				"NDEx Neighborhood Query/v1.1 (Depth=1; Query terms=\""+ genes.stream().collect(Collectors.joining(","))
				+ "\")"));
		
			writeOtherAspectsForSubnetwork(netUUIDStr, newNodeIds, edgeIds, writer, md, postmd,
				"Neighborhood query result on network" , provenanceRecords, nodeIds);
		
			writer.writeMetadata(postmd);
			writer.end();
		}
		
		//rename the file 
		java.nio.file.Path src = Paths.get(tmpFileName);
		java.nio.file.Path tgt = Paths.get(App.getWorkingPath() + "/result/" + taskId.toString() + "/" + netUUIDStr);		
		Files.move(src, tgt, StandardCopyOption.ATOMIC_MOVE); 				
		
		long t2 = Calendar.getInstance().getTimeInMillis();

		accLogger.info("Total " + (t2-t1)/1000f + " seconds. Returned " + edgeIds.size() + " edges and " + newNodeIds.size() + " nodes.",
				new Object[]{});
	} */

	private static InteractomeSearchResult runQuery(UUID taskId, NetworkShortSummary summary, final Set<Long> nodeIds, List<String> genes,
			Hashtable<String,Object> status, Set<String> hitgenes) throws IOException, NdexException {
	   
		if(summary.getType().equals("i") ) {
		   PPIQueryType startingQueryType = PPIQueryType.Direct;
		   
		   if ( (summary.getEdgeCount() > 60000 ) && 
				   (summary.getEdgeCount() > 400000 || (summary.getEdgeCount() / summary.getNodeCount()) > 20 ) ) 
			   startingQueryType = PPIQueryType.Neighborhood;
			   
		   return queryPPINetwork(taskId,summary.getUuid(), nodeIds, genes,
					   status, hitgenes, startingQueryType);
	   }  
	   
	   return adjacentQuery(taskId,summary.getUuid(), nodeIds, genes,
				   status, hitgenes, false);
	   
	}
	
	
	private static InteractomeSearchResult queryPPINetwork(UUID taskId, String networkIdStr, final Set<Long> nodeIds, List<String> genes,
			Hashtable<String,Object> status, Set<String> hitgenes, PPIQueryType startingType) throws IOException, NdexException {
		
		if ( startingType == PPIQueryType.Neighborhood ) {
			InteractomeSearchResult result = adjacentQuery(taskId,networkIdStr, nodeIds, genes,
					   status, hitgenes, true);
			
			if (result.getEdgeCount() > edgeLimit) {
				return queryPPINetwork(taskId,networkIdStr, nodeIds, genes, status, hitgenes, PPIQueryType.Interconnect);
			}
			return result;
		}
		
		if (startingType == PPIQueryType.Interconnect ) {
			InteractomeSearchResult result = interConnectQuery(taskId,networkIdStr, nodeIds, genes,
					   status, hitgenes);
			
			if (result.getEdgeCount() > edgeLimit) {
				return queryPPINetwork(taskId,networkIdStr, nodeIds, genes, status, hitgenes, PPIQueryType.Direct);
			}
			return result;
		}
		
		if ( startingType == PPIQueryType.Direct ) {
			return directQuery(taskId,networkIdStr, nodeIds, genes,
					   status, hitgenes);
		}
		
		throw new NdexException ("Invalid query type " + startingType.name() + " passed to queryPPINetwork function."); 
	}

	
	private static InteractomeSearchResult interConnectQuery(UUID taskId, String netUUIDStr, final Set<Long> nodeIds, List<String> genes,
			Hashtable<String,Object> status, Set<String> hitgenes) throws IOException {
		long t1 = Calendar.getInstance().getTimeInMillis();
		
		Map<Long, EdgesElement> edgeTable = new TreeMap<> ();
		Set<Long> finalEdgeIds  ;


		//NodeId -> unique neighbor node ids
		Map<Long,NodeDegreeHelper> nodeNeighborIdTable = new TreeMap<>();
		
		InteractomeSearchResult currentResult;
		//s.setNodeCount(nodeIds.size());
		
		String tmpFileName = App.getWorkingPath() + "/result/" + taskId.toString() + "/tmp_" + netUUIDStr;
		
		try (FileOutputStream out = new FileOutputStream (tmpFileName) ) {
		
			NdexCXNetworkWriter writer = new NdexCXNetworkWriter(out, true);
			MetaDataCollection md = prepareMetadata(netUUIDStr) ;
			writer.start();
			writer.writeMetadata(md);
		
			MetaDataCollection postmd = new MetaDataCollection();
		
			writeContextAspect(netUUIDStr, writer, md, postmd);

			if (md.getMetaDataElement(EdgesElement.ASPECT_NAME) != null) {
				try (AspectIterator<EdgesElement> ei = new AspectIterator<>(netUUIDStr,
						EdgesElement.ASPECT_NAME, EdgesElement.class, pathPrefix )) {
					while (ei.hasNext()) {
						EdgesElement edge = ei.next();					
						if (nodeIds.contains(edge.getSource())) {
							edgeTable.put(edge.getId(), edge);
							if ( ! nodeIds.contains(edge.getTarget())) {
								NodeDegreeHelper h = nodeNeighborIdTable.get(edge.getTarget());
								if (h != null && h.isToBeDeleted()) {
									if (h.getNodeId().equals(edge.getSource())) {
										h.addEdge(edge.getId());
									} else {
										h.setToBeDeleted(false);
										h.removeAllEdges();
									}
								} else if ( h == null) {
									NodeDegreeHelper newHelper = new NodeDegreeHelper(edge.getSource(), edge.getId());
									nodeNeighborIdTable.put(edge.getTarget(), newHelper);
								}
							}
						} else if (nodeIds.contains(edge.getTarget())) {
//							writer.writeElement(edge);
							edgeTable.put(edge.getId(), edge);
							if ( ! nodeIds.contains(edge.getSource())) {
								NodeDegreeHelper h = nodeNeighborIdTable.get(edge.getSource());
							
								if (h != null && h.isToBeDeleted() ) {
									if (h.getNodeId().equals(edge.getTarget())) {
										h.addEdge(edge.getId());
									} else {
										h.setToBeDeleted(false);
										h.removeAllEdges();
									}
								} else if ( h == null) {
									NodeDegreeHelper newHelper = new NodeDegreeHelper(edge.getTarget(), edge.getId());
									nodeNeighborIdTable.put(edge.getSource(), newHelper);
								}
							}
						}

					}
				}
			}
			
			System.out.println( edgeTable.size()  + " edges from 2-step interconnect query.");
			//trim the nodes that only connect to one starting nodes.
			Set<Long> finalNodes = new TreeSet<>();
			for (Map.Entry<Long, NodeDegreeHelper> e : nodeNeighborIdTable.entrySet()) {
				NodeDegreeHelper h = e.getValue();
				if ( h.isToBeDeleted()) {
					for ( Long edgeId : h.getEdgeIds())
						edgeTable.remove( edgeId);				
				} else {
					finalNodes.add(e.getKey());
				}
			}
			
			System.out.println( edgeTable.size()  + " edges after trim.");
			
			finalEdgeIds = new TreeSet<>(edgeTable.keySet());
			
			if (edgeTable.size() > edgeLimit)  {
				currentResult = createResult(netUUIDStr, hitgenes, finalNodes.size(), edgeTable.size());
			} else {	
				// write edge aspect
				writer.startAspectFragment(EdgesElement.ASPECT_NAME);
				writer.openFragment();

				// write the edges in the table first
				if (edgeTable.size() > 0) {

					for (EdgesElement e : edgeTable.values()) {
						writer.writeElement(e);
					}

				}
				
				// write extra edges that found between the new neighboring nodes.

				if (finalNodes.size() > 0 && md.getMetaDataElement(EdgesElement.ASPECT_NAME) != null) {
					try (AspectIterator<EdgesElement> ei = new AspectIterator<>(netUUIDStr, EdgesElement.ASPECT_NAME,
							EdgesElement.class, pathPrefix)) {
						while (ei.hasNext()) {
							EdgesElement edge = ei.next();
							if ((!finalEdgeIds.contains(edge.getId())) && finalNodes.contains(edge.getSource())
									&& finalNodes.contains(edge.getTarget())) {
								writer.writeElement(edge);
								finalEdgeIds.add(edge.getId());
								
								if (finalEdgeIds.size() > edgeLimit)  {
									break;
								}
							}
						}
					}
				}

				
				writer.closeFragment();
				writer.endAspectFragment();
				System.out.println("Query returned " + writer.getFragmentLength() + " edges.");
				
				if (finalEdgeIds.size() > edgeLimit) {
					currentResult = createResult(netUUIDStr, hitgenes, finalNodes.size(), finalEdgeIds.size());
				} else {

					MetaDataElement mde = new MetaDataElement(EdgesElement.ASPECT_NAME, mdeVer);
					mde.setElementCount(Long.valueOf(finalEdgeIds.size()));
					mde.setIdCounter(Collections.max(finalEdgeIds));
					postmd.add(mde);

					System.out.println("done writing out edges.");

					finalNodes.addAll(nodeIds);

					status.put(PROGRESS, 20);

					// write nodes
					writer.startAspectFragment(NodesElement.ASPECT_NAME);
					writer.openFragment();
					try (AspectIterator<NodesElement> ei = new AspectIterator<>(netUUIDStr, NodesElement.ASPECT_NAME,
							NodesElement.class, pathPrefix)) {
						while (ei.hasNext()) {
							NodesElement node = ei.next();
							if (finalNodes.contains(Long.valueOf(node.getId()))) {
								writer.writeElement(node);
							}
						}
					}
					writer.closeFragment();
					writer.endAspectFragment();
					if (nodeIds.size() > 0) {
						MetaDataElement mde1 = new MetaDataElement(NodesElement.ASPECT_NAME, mdeVer);
						mde1.setElementCount(Long.valueOf(finalNodes.size()));
						mde1.setIdCounter(nodeIds.isEmpty() ? 0L : Collections.max(nodeIds));
						postmd.add(mde1);
					}

					status.put(PROGRESS, 40);
					currentResult = createResult(netUUIDStr, hitgenes, finalNodes.size(), finalEdgeIds.size());

					ArrayList<NetworkAttributesElement> provenanceRecords = new ArrayList<>(2);
					provenanceRecords.add(new NetworkAttributesElement(null, "prov:wasDerivedFrom", netUUIDStr));
					provenanceRecords.add(new NetworkAttributesElement(null, "prov:wasGeneratedBy",
							"NDEx Interactome Query/v1.1 (Query terms=\""
									+ genes.stream().collect(Collectors.joining(",")) + "\")"));

					writeOtherAspectsForSubnetwork(netUUIDStr, finalNodes, finalEdgeIds, writer, md, postmd,
							"Interactome query result on network", provenanceRecords, nodeIds);

					status.put(PROGRESS, 95);
					writer.writeMetadata(postmd);
				}
			}
			writer.end();
		}
		
		java.nio.file.Path src = Paths.get(tmpFileName);
		if (finalEdgeIds.size() > edgeLimit) {
			src.toFile().delete(); // remove the temp result file
		} else {
			//rename the file 
			java.nio.file.Path tgt = Paths
					.get(App.getWorkingPath() + "/result/" + taskId.toString() + "/" + netUUIDStr + ".cx");
			Files.move(src, tgt, StandardCopyOption.ATOMIC_MOVE);

			long t2 = Calendar.getInstance().getTimeInMillis();
			status.put("wallTime", Long.valueOf(t2 - t1));
			accLogger.info("Total " + (t2 - t1) / 1000f + " seconds. Returned " + finalEdgeIds.size() + " edges and "
					+ nodeIds.size() + " nodes.", new Object[] {});
		}
		
		return currentResult;
	}
	
	private static InteractomeSearchResult createResult(String netUUIDStr,Set<String> hitgenes, int nodeCount, int edgeCount ) {
		InteractomeSearchResult currentResult = new  InteractomeSearchResult();
		currentResult.setNetworkUUID(netUUIDStr);
		currentResult.setHitGenes(hitgenes);
		NetworkShortSummary summary = App.getDBTable().get(netUUIDStr);
		currentResult.setDescription(summary.getName() + ", parent network size: " + 
				   summary.getNodeCount() + " nodes, " + summary.getEdgeCount() + " edges"
		        );
		currentResult.setNodeCount(nodeCount);
		currentResult.setEdgeCount(edgeCount);
		currentResult.setImageURL(summary.getImageURL());
		currentResult.setPercentOverlap(edgeCount*100/summary.getEdgeCount());
		return currentResult;
	}

	
	private static InteractomeSearchResult directQuery(UUID taskId, String netUUIDStr, final Set<Long> nodeIds, List<String> genes,
			Hashtable<String,Object> status, Set<String> hitgenes) throws IOException {
		long t1 = Calendar.getInstance().getTimeInMillis();
		Set<Long> edgeIds = new TreeSet<> ();
		
		InteractomeSearchResult currentResult;
		
		String tmpFileName = App.getWorkingPath() + "/result/" + taskId.toString() + "/tmp_" + netUUIDStr;
		
		try (FileOutputStream out = new FileOutputStream (tmpFileName) ) {
		
			NdexCXNetworkWriter writer = new NdexCXNetworkWriter(out, true);
			MetaDataCollection md = prepareMetadata(netUUIDStr) ;
			writer.start();
			writer.writeMetadata(md);
		
			MetaDataCollection postmd = new MetaDataCollection();
		
			writeContextAspect(netUUIDStr, writer, md, postmd);

			//write nodes
			writer.startAspectFragment(NodesElement.ASPECT_NAME);
			writer.openFragment();
			try (AspectIterator<NodesElement> ei = new AspectIterator<>(netUUIDStr, NodesElement.ASPECT_NAME, NodesElement.class, pathPrefix)) {
				while (ei.hasNext()) {
					NodesElement node = ei.next();
					if (nodeIds.contains(Long.valueOf(node.getId()))) {
						writer.writeElement(node);
					}
				}
			}
			writer.closeFragment();
			writer.endAspectFragment();
			if ( nodeIds.size()>0) {
				MetaDataElement mde1 = new MetaDataElement(NodesElement.ASPECT_NAME,mdeVer);
				mde1.setElementCount((long)nodeIds.size());
				mde1.setIdCounter(nodeIds.isEmpty()? 0L : Collections.max(nodeIds));
				postmd.add(mde1);
			}
			
			status.put(PROGRESS, 15);
			
			//write edges
			if (md.getMetaDataElement(EdgesElement.ASPECT_NAME) != null) {
				writer.startAspectFragment(EdgesElement.ASPECT_NAME);
				writer.openFragment();
			
				try (AspectIterator<EdgesElement> ei = new AspectIterator<>( netUUIDStr,EdgesElement.ASPECT_NAME, EdgesElement.class, pathPrefix)) {
					while (ei.hasNext()) {
						EdgesElement edge = ei.next();
						if (nodeIds.contains(edge.getSource())
									&& nodeIds.contains(edge.getTarget())) {
								writer.writeElement(edge);
								edgeIds.add(edge.getId());
						}

					}
				}
				
				accLogger.info("Query returned " + writer.getFragmentLength() + " edges.");
				writer.closeFragment();
				writer.endAspectFragment();
			
			}
	
			MetaDataElement mde = new MetaDataElement(EdgesElement.ASPECT_NAME,mdeVer);
			mde.setElementCount((long)edgeIds.size());
			mde.setIdCounter(edgeIds.isEmpty()? 0L : Collections.max(edgeIds));
			postmd.add(mde);

			status.put(PROGRESS, 40);
			currentResult = createResult(netUUIDStr, hitgenes,nodeIds.size(), edgeIds.size());

			ArrayList<NetworkAttributesElement> provenanceRecords = new ArrayList<> (2);
			provenanceRecords.add(new NetworkAttributesElement (null, "prov:wasDerivedFrom", netUUIDStr));
			provenanceRecords.add(new NetworkAttributesElement (null, "prov:wasGeneratedBy",
				"NDEx Interactome Query/v1.1 (Query terms=\""+ genes.stream().collect(Collectors.joining(","))
				+ "\")"));
		
			writeOtherAspectsForSubnetwork(netUUIDStr, nodeIds, edgeIds, writer, md, postmd,
				"Interactome query result on network" , provenanceRecords, nodeIds);
		
			status.put(PROGRESS, 95);
			writer.writeMetadata(postmd);
			writer.end();
		}
		
		//rename the file 
		java.nio.file.Path src = Paths.get(tmpFileName);
		java.nio.file.Path tgt = Paths.get(App.getWorkingPath() + "/result/" + taskId.toString() + "/" + netUUIDStr + ".cx");		
		Files.move(src, tgt, StandardCopyOption.ATOMIC_MOVE); 				
		
		long t2 = Calendar.getInstance().getTimeInMillis();
        status.put("wallTime", Long.valueOf(t2-t1));
		accLogger.info("Total " + (t2-t1)/1000f + " seconds. Returned " + edgeIds.size() + " edges and " + nodeIds.size() + " nodes.",
				new Object[]{});
		
		return currentResult;
	}
	
	
	private static void writeOtherAspectsForSubnetwork(String netUUID, Set<Long> nodeIds, Set<Long> edgeIds, NdexCXNetworkWriter writer,
			MetaDataCollection md, MetaDataCollection postmd, String networkNamePrefix,
			Collection<NetworkAttributesElement> extraNetworkAttributes, Set<Long> queryNodeIds) throws IOException, JsonProcessingException {
		//process node attribute aspect
		if (nodeIds.size() > 0 || md.getMetaDataElement(NodeAttributesElement.ASPECT_NAME) != null) {
			writer.startAspectFragment(NodeAttributesElement.ASPECT_NAME);
			writer.openFragment();
			
			//write the querynode attributes first
			for (Long nodeId: queryNodeIds) {
				writer.writeElement( new NodeAttributesElement(null, nodeId,"querynode", "true", ATTRIBUTE_DATA_TYPE.BOOLEAN));
			}
			
			// filter other node attributes
			try (AspectIterator<NodeAttributesElement> ei = new AspectIterator<>(netUUID, NodeAttributesElement.ASPECT_NAME, NodeAttributesElement.class, pathPrefix)) {
					while (ei.hasNext()) {
						NodeAttributesElement nodeAttr = ei.next();
						if (nodeIds.contains(nodeAttr.getPropertyOf())) {
								writer.writeElement(nodeAttr);
						}
					}
			}
			writer.closeFragment();
			writer.endAspectFragment();
			MetaDataElement mde = new MetaDataElement(NodeAttributesElement.ASPECT_NAME,mdeVer);
			mde.setElementCount(writer.getFragmentLength());
			postmd.add(mde);
		}
		
		//process edge attribute aspect
		if (md.getMetaDataElement(EdgeAttributesElement.ASPECT_NAME) != null) {
			writer.startAspectFragment(EdgeAttributesElement.ASPECT_NAME);
			writer.openFragment();
			try (AspectIterator<EdgeAttributesElement> ei = new AspectIterator<>(netUUID,EdgeAttributesElement.ASPECT_NAME, EdgeAttributesElement.class, pathPrefix)) {
					while (ei.hasNext()) {
						EdgeAttributesElement edgeAttr = ei.next();
						if (edgeIds.contains(edgeAttr.getPropertyOf())) {
								writer.writeElement(edgeAttr);
						}
					}
			}
			writer.closeFragment();
			writer.endAspectFragment();
			MetaDataElement mde = new MetaDataElement(EdgeAttributesElement.ASPECT_NAME,mdeVer);
			mde.setElementCount(writer.getFragmentLength());
			postmd.add(mde);
		}
		
		//write networkAttributes
		writer.startAspectFragment(NetworkAttributesElement.ASPECT_NAME);
		writer.openFragment();

/*		if (limitIsOver) {
			writer.writeElement(new NetworkAttributesElement(null, "EdgeLimitExceeded", "true"));
		} */
		if (md.getMetaDataElement(NetworkAttributesElement.ASPECT_NAME) != null) {
			try (AspectIterator<NetworkAttributesElement> ei = new AspectIterator<>(netUUID,NetworkAttributesElement.ASPECT_NAME, NetworkAttributesElement.class, pathPrefix)) {
				while (ei.hasNext()) {
					NetworkAttributesElement attr = ei.next();
					if (attr.getName().equals("name"))
						attr.setSingleStringValue(networkNamePrefix + " - " + attr.getValue());
					writer.writeElement(attr);
				}
			}
		}
		
		for ( NetworkAttributesElement attr : extraNetworkAttributes) {
			writer.writeElement(attr);			
		}
		
		writer.closeFragment();
		writer.endAspectFragment();
		MetaDataElement mde2 = new MetaDataElement(NetworkAttributesElement.ASPECT_NAME, mdeVer);
		mde2.setElementCount( writer.getFragmentLength());
		postmd.add(mde2);

		//process cyVisualProperty aspect
		
		if (md.getMetaDataElement(CyVisualPropertiesElement.ASPECT_NAME) != null) {
			writer.startAspectFragment(CyVisualPropertiesElement.ASPECT_NAME);
			writer.openFragment();

			// commenting this out. We are switching back to use the original sytle on the networks.
			/*for (AspectElement e : App.getVisualSytleTemplate()) {
				CyVisualPropertiesElement elmt = (CyVisualPropertiesElement)e;
				if ( elmt.getProperties_of().equals("nodes")) {
					if ( nodeIds.contains(elmt.getApplies_to())) {
						writer.writeElement(elmt);
					}
				} else if (elmt.getProperties_of().equals("edges")) {
					if ( edgeIds.contains(elmt.getApplies_to())) {
						writer.writeElement(elmt);
					}
				} else {
					writer.writeElement(elmt);
				}
			} */
			try (AspectIterator<CyVisualPropertiesElement> it = new AspectIterator<>(netUUID,
					CyVisualPropertiesElement.ASPECT_NAME, 
							CyVisualPropertiesElement.class, pathPrefix)) {
				while (it.hasNext()) {
					CyVisualPropertiesElement elmt = it.next();
					if ( elmt.getProperties_of().equals("nodes")) {
						if ( nodeIds.contains(elmt.getApplies_to())) {
							writer.writeElement(elmt);
						}
					} else if (elmt.getProperties_of().equals("edges")) {
						if ( edgeIds.contains(elmt.getApplies_to())) {
							writer.writeElement(elmt);
						}
					} else {
						writer.writeElement(elmt);
					}
				}
			}
			writer.closeFragment();
			writer.endAspectFragment();
			MetaDataElement mde = new MetaDataElement(CyVisualPropertiesElement.ASPECT_NAME,mdeVer);
			mde.setElementCount(writer.getFragmentLength());
			postmd.add(mde);
		}	
		
		// process function terms
		
		if (md.getMetaDataElement(FunctionTermElement.ASPECT_NAME) != null) {
			writer.startAspectFragment(FunctionTermElement.ASPECT_NAME);
			writer.openFragment();
			try (AspectIterator<FunctionTermElement> ei = new AspectIterator<>(netUUID,
					FunctionTermElement.ASPECT_NAME, FunctionTermElement.class, pathPrefix)) {
					while (ei.hasNext()) {
						FunctionTermElement ft = ei.next();
						if (nodeIds.contains(ft.getNodeID())) {
								writer.writeElement(ft);
						}
					}
			}
			writer.closeFragment();
			writer.endAspectFragment();
			MetaDataElement mde = new MetaDataElement(FunctionTermElement.ASPECT_NAME,mdeVer);
			mde.setElementCount(writer.getFragmentLength());
			postmd.add(mde);
		}	
		
		
		Set<Long> citationIds = new TreeSet<> ();
		
		//process citation links aspects
		if (md.getMetaDataElement(NodeCitationLinksElement.ASPECT_NAME) != null) {
			writer.startAspectFragment(NodeCitationLinksElement.ASPECT_NAME);
			writer.openFragment();
			NodeCitationLinksElement worker = new NodeCitationLinksElement();
			try (AspectIterator<NodeCitationLinksElement> ei = new AspectIterator<>(netUUID,
					NodeCitationLinksElement.ASPECT_NAME, NodeCitationLinksElement.class, pathPrefix)) {
					while (ei.hasNext()) {
						NodeCitationLinksElement ft = ei.next();
						worker.getSourceIds().clear();
						for ( Long nid : ft.getSourceIds()) {
							if ( nodeIds.contains(nid))
								worker.getSourceIds().add(nid);
						}
						if ( worker.getSourceIds().size()>0) {
							worker.setCitationIds(ft.getCitationIds());
							writer.writeElement(worker);
							citationIds.addAll(worker.getCitationIds());
						}	
					}
			}
			writer.closeFragment();
			writer.endAspectFragment();
			MetaDataElement mde = new MetaDataElement(NodeCitationLinksElement.ASPECT_NAME,mdeVer);
			mde.setElementCount(writer.getFragmentLength());
			postmd.add(mde);
		}	
				
		
		if (md.getMetaDataElement(EdgeCitationLinksElement.ASPECT_NAME) != null) {
			EdgeCitationLinksElement worker = new EdgeCitationLinksElement();
			writer.startAspectFragment(EdgeCitationLinksElement.ASPECT_NAME);
			writer.openFragment();
			try (AspectIterator<EdgeCitationLinksElement> ei = new AspectIterator<>(netUUID,
					EdgeCitationLinksElement.ASPECT_NAME, EdgeCitationLinksElement.class, pathPrefix)) {
					while (ei.hasNext()) {
						EdgeCitationLinksElement ft = ei.next();
						for ( Long nid : ft.getSourceIds()) {
							if ( edgeIds.contains(nid))
								worker.getSourceIds().add(nid);
						}
						if ( worker.getSourceIds().size()>0) {
							worker.setCitationIds(ft.getCitationIds());
							writer.writeElement(worker);
							citationIds.addAll(worker.getCitationIds());
							worker.getSourceIds().clear();
						}	
					}
			}
			writer.closeFragment();
			writer.endAspectFragment();
			MetaDataElement mde = new MetaDataElement(EdgeCitationLinksElement.ASPECT_NAME,mdeVer);
			mde.setElementCount(writer.getFragmentLength());
			postmd.add(mde);
		}	
				
			
		if( !citationIds.isEmpty()) {
			long citationCntr = 0;
			writer.startAspectFragment(CitationElement.ASPECT_NAME);
			writer.openFragment();
			try (AspectIterator<CitationElement> ei = new AspectIterator<>(netUUID,
				CitationElement.ASPECT_NAME, CitationElement.class, pathPrefix)) {
				while (ei.hasNext()) {
					CitationElement ft = ei.next();
					if ( citationIds.contains(ft.getId())) {
						writer.writeElement(ft);
						if (ft.getId() > citationCntr)
							citationCntr = ft.getId();
					}	
				}	
			}
			
			writer.closeFragment();
			writer.endAspectFragment();
			MetaDataElement mde = new MetaDataElement(CitationElement.ASPECT_NAME,mdeVer);
			mde.setElementCount(writer.getFragmentLength());
			mde.setIdCounter(citationCntr);
			postmd.add(mde);
		}	

		// support and related aspects
		Set<Long> supportIds = new TreeSet<> ();
		
		//process support links aspects
		if (md.getMetaDataElement(NodeSupportLinksElement.ASPECT_NAME) != null) {
			NodeSupportLinksElement worker = new NodeSupportLinksElement();
			writer.startAspectFragment(NodeSupportLinksElement.ASPECT_NAME);
			writer.openFragment();
			try (AspectIterator<NodeSupportLinksElement> ei = new AspectIterator<>(netUUID,
					NodeSupportLinksElement.ASPECT_NAME, NodeSupportLinksElement.class, pathPrefix)) {
					while (ei.hasNext()) {
						NodeSupportLinksElement ft = ei.next();
						for ( Long nid : ft.getSourceIds()) {
							if ( nodeIds.contains(nid))
								worker.getSourceIds().add(nid);
						}
						if ( worker.getSourceIds().size()>0) {
							worker.setSupportIds(ft.getSupportIds());
							writer.writeElement(worker);
							supportIds.addAll(worker.getSupportIds());
							worker.getSourceIds().clear();
						}	
					}
			}
			writer.closeFragment();
			writer.endAspectFragment();
			MetaDataElement mde = new MetaDataElement(NodeSupportLinksElement.ASPECT_NAME,mdeVer);
			mde.setElementCount(writer.getFragmentLength());
			postmd.add(mde);
		}	
				
		
		if (md.getMetaDataElement(EdgeSupportLinksElement.ASPECT_NAME) != null) {
			EdgeSupportLinksElement worker = new EdgeSupportLinksElement();
			writer.startAspectFragment(EdgeSupportLinksElement.ASPECT_NAME);
			writer.openFragment();
			try (AspectIterator<EdgeSupportLinksElement> ei = new AspectIterator<>(netUUID,
					EdgeSupportLinksElement.ASPECT_NAME, EdgeSupportLinksElement.class, pathPrefix)) {
					while (ei.hasNext()) {
						EdgeSupportLinksElement ft = ei.next();
						for ( Long nid : ft.getSourceIds()) {
							if ( edgeIds.contains(nid))
								worker.getSourceIds().add(nid);
						}
						if ( worker.getSourceIds().size()>0) {
							worker.setSupportIds(ft.getSupportIds());
							writer.writeElement(worker);
							supportIds.addAll(worker.getSupportIds());
							worker.getSourceIds().clear();
						}	
					}
			}
			writer.closeFragment();
			writer.endAspectFragment();
			MetaDataElement mde = new MetaDataElement(EdgeSupportLinksElement.ASPECT_NAME,mdeVer);
			mde.setElementCount(writer.getFragmentLength());
			postmd.add(mde);
		}	

				
		if( !supportIds.isEmpty()) {
			long supportCntr = 0;
			writer.startAspectFragment(SupportElement.ASPECT_NAME);
			writer.openFragment();
			try (AspectIterator<SupportElement> ei = new AspectIterator<>(netUUID,
					SupportElement.ASPECT_NAME, SupportElement.class, pathPrefix)) {
				while (ei.hasNext()) {
					SupportElement ft = ei.next();
					if ( supportIds.contains(ft.getId())) {
						writer.writeElement(ft);
						if ( supportCntr < ft.getId())
							supportCntr=ft.getId();
					}	
				}	
			}
			
			writer.closeFragment();
			writer.endAspectFragment();
			MetaDataElement mde = new MetaDataElement(SupportElement.ASPECT_NAME,mdeVer);
			mde.setElementCount(writer.getFragmentLength());
			mde.setIdCounter(supportCntr);
			postmd.add(mde);
					
		}
	}
	
	
	private static MetaDataCollection prepareMetadata(String netUUID) {
		MetaDataCollection md = new MetaDataCollection();
		File dir = new File(pathPrefix+netUUID+"/aspects");
		  File[] directoryListing = dir.listFiles();
		  for (File child : directoryListing) {
			  String aspName = child.getName();
			  MetaDataElement e;
			  //TODO: clean this up later to get rid of old visualProperties aspect.
			  if (aspName.equals("visualProperties")) {
				   e = new MetaDataElement (CyVisualPropertiesElement.ASPECT_NAME, mdeVer);
			  } else 
			       e = new MetaDataElement (aspName, mdeVer);
			  e.setConsistencyGroup(consistencyGrp);
			  md.add(e);			  
		  }
		  
		  return md;
	}	
	
	/**
	 * This function combines adjacent and neighborhood query together.
	 * 
	 * @param taskId
	 * @param netUUIDStr
	 * @param nodeIds
	 * @param genes
	 * @param status
	 * @param hitgenes
	 * @param fullNeighborhood  If true, run the neighborhood query, otherwise run the adjacent query.
	 * @return
	 * @throws IOException
	 */
	
	private static InteractomeSearchResult adjacentQuery(UUID taskId, String netUUIDStr, final Set<Long> nodeIds,
			List<String> genes, Hashtable<String, Object> status, Set<String> hitgenes, boolean fullNeighborhood)
			throws IOException {

		long t1 = Calendar.getInstance().getTimeInMillis();

		Set<Long> edgeIds = new TreeSet<>();

		InteractomeSearchResult currentResult;

		String tmpFileName = App.getWorkingPath() + "/result/" + taskId.toString() + "/tmp_" + netUUIDStr;

		try (FileOutputStream out = new FileOutputStream(tmpFileName)) {

			NdexCXNetworkWriter writer = new NdexCXNetworkWriter(out, true);
			MetaDataCollection md = prepareMetadata(netUUIDStr);
			writer.start();
			writer.writeMetadata(md);

			MetaDataCollection postmd = new MetaDataCollection();

			writeContextAspect(netUUIDStr, writer, md, postmd);

			Set<Long> finalNodeIds = new TreeSet<>();

			if (md.getMetaDataElement(EdgesElement.ASPECT_NAME) != null) {

				writer.startAspectFragment(EdgesElement.ASPECT_NAME);
				writer.openFragment();

				try (AspectIterator<EdgesElement> ei = new AspectIterator<>(netUUIDStr, EdgesElement.ASPECT_NAME,
						EdgesElement.class, pathPrefix)) {
					while (ei.hasNext()) {
						EdgesElement edge = ei.next();
						if (nodeIds.contains(edge.getSource()) || nodeIds.contains(edge.getTarget())) {
							writer.writeElement(edge);
							edgeIds.add(edge.getId());

							// check if it is over the limit
							if (fullNeighborhood && edgeIds.size() > edgeLimit)
								break;

							if (!nodeIds.contains(edge.getSource()))
								finalNodeIds.add(edge.getSource());
							else if (!nodeIds.contains(edge.getTarget()))
								finalNodeIds.add(edge.getTarget());
						}
					}
				}

				writer.closeFragment();
				writer.endAspectFragment();
				System.out.println("Query returned " + writer.getFragmentLength() + " edges.");

			}

			finalNodeIds.addAll(nodeIds);

			status.put(PROGRESS, 20);

			if (fullNeighborhood && edgeIds.size() > edgeLimit) {  // result too big, we stop now.
				currentResult = createResult(netUUIDStr, hitgenes, finalNodeIds.size(), edgeIds.size());
				writer.end();
			} else {

				// write nodes
				writer.startAspectFragment(NodesElement.ASPECT_NAME);
				writer.openFragment();
				try (AspectIterator<NodesElement> ei = new AspectIterator<>(netUUIDStr, NodesElement.ASPECT_NAME,
						NodesElement.class, pathPrefix)) {
					while (ei.hasNext()) {
						NodesElement node = ei.next();
						if (finalNodeIds.contains(Long.valueOf(node.getId()))) {
							writer.writeElement(node);
						}
					}
				}
				writer.closeFragment();
				writer.endAspectFragment();

				if (finalNodeIds.size() > 0) {
					MetaDataElement mde1 = new MetaDataElement(NodesElement.ASPECT_NAME, mdeVer);
					mde1.setElementCount(Long.valueOf(finalNodeIds.size()));
					mde1.setIdCounter(finalNodeIds.isEmpty() ? 0L : Collections.max(finalNodeIds));
					postmd.add(mde1);
				}

				// check if we need to output the full neighborhood.
				if (fullNeighborhood && md.getMetaDataElement(EdgesElement.ASPECT_NAME) != null) {
					writer.startAspectFragment(EdgesElement.ASPECT_NAME);
					writer.openFragment();

					try (AspectIterator<EdgesElement> ei = new AspectIterator<>(netUUIDStr, EdgesElement.ASPECT_NAME,
							EdgesElement.class, pathPrefix)) {
						while (ei.hasNext()) {
							EdgesElement edge = ei.next();
							if ((!edgeIds.contains(edge.getId())) && finalNodeIds.contains(edge.getSource())
									&& finalNodeIds.contains(edge.getTarget())) {
								writer.writeElement(edge);
								edgeIds.add(edge.getId());
								
								if (edgeIds.size() > edgeLimit)
									break;
							}
						}
					}
					writer.closeFragment();
					writer.endAspectFragment();
				}

				if (md.getMetaDataElement(EdgesElement.ASPECT_NAME) != null) {
					MetaDataElement mde = new MetaDataElement(EdgesElement.ASPECT_NAME, mdeVer);
					mde.setElementCount((long) edgeIds.size());
					mde.setIdCounter(edgeIds.isEmpty() ? 0L : Collections.max(edgeIds));
					postmd.add(mde);
				}

				currentResult = createResult(netUUIDStr, hitgenes, finalNodeIds.size(), edgeIds.size());
				
				if (!fullNeighborhood || edgeIds.size() <= edgeLimit) {

					status.put(PROGRESS, 40);
					ArrayList<NetworkAttributesElement> provenanceRecords = new ArrayList<>(2);
					provenanceRecords.add(new NetworkAttributesElement(null, "prov:wasDerivedFrom", netUUIDStr));
					provenanceRecords.add(new NetworkAttributesElement(null, "prov:wasGeneratedBy",
							"NDEx Interactome Query/v1.1 (Query terms=\""
									+ genes.stream().collect(Collectors.joining(",")) + "\")"));

					writeOtherAspectsForSubnetwork(netUUIDStr, finalNodeIds, edgeIds, writer, md, postmd,
							"Interactome query result on network", provenanceRecords, nodeIds);

					status.put(PROGRESS, 95);
					writer.writeMetadata(postmd);
				}
				writer.end();
			}
		}

		java.nio.file.Path src = Paths.get(tmpFileName);

		if (fullNeighborhood && edgeIds.size() > edgeLimit) {
			src.toFile().delete(); // remove the temp result file
		} else {
			// rename the file
			java.nio.file.Path tgt = Paths
					.get(App.getWorkingPath() + "/result/" + taskId.toString() + "/" + netUUIDStr + ".cx");
			Files.move(src, tgt, StandardCopyOption.ATOMIC_MOVE);

			long t2 = Calendar.getInstance().getTimeInMillis();
			status.put("wallTime", Long.valueOf(t2 - t1));
			accLogger.info("Total " + (t2 - t1) / 1000f + " seconds. Returned " + edgeIds.size() + " edges.",
					new Object[] {});
		}
		
		return currentResult;

	}
	
	private static void writeContextAspect(String netUUID, NdexCXNetworkWriter writer, MetaDataCollection md, MetaDataCollection postmd)
			throws IOException, JsonProcessingException {
		//process namespace aspect	
		if (md.getMetaDataElement(NamespacesElement.ASPECT_NAME) != null) {
			writer.startAspectFragment(NamespacesElement.ASPECT_NAME);
			writer.openFragment();
			try (AspectIterator<NamespacesElement> ei = new AspectIterator<>(netUUID,
						NamespacesElement.ASPECT_NAME, NamespacesElement.class, pathPrefix)) {
				while (ei.hasNext()) {
						NamespacesElement node = ei.next();
							writer.writeElement(node);
				}
			}

			writer.closeFragment();
			writer.endAspectFragment();	
			MetaDataElement mde = new MetaDataElement(NamespacesElement.ASPECT_NAME,mdeVer);
			mde.setElementCount(1L);
			postmd.add(mde);
		}
	}

	/*
	public class NodeDegreeHelper {
		
		private boolean tobeDeleted;
		private Long nodeId;
		private List<Long> edgeIds;
		

		public NodeDegreeHelper (Long newNodeId, Long newEdgeId) {
			this.setToBeDeleted(true);
			edgeIds = new ArrayList<>();
			this.nodeId = newNodeId;
			edgeIds.add(newEdgeId);
		}

		public boolean isToBeDeleted() {
			return tobeDeleted;
		}

		public void setToBeDeleted(boolean toBeDeleted) {
			this.tobeDeleted = toBeDeleted;
		}

		public Long getNodeId() {
			return nodeId;
		}


		public List<Long> getEdgeIds() {
			return edgeIds;
		}
		
		public void addEdge(Long id) { this.edgeIds.add(id); }
		
		public void removeAllEdges() {edgeIds = null;}

	}
*/
}
