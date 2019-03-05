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
import org.ndexbio.interactomesearch.object.InteractomeResultNetworkSummary;
import org.ndexbio.interactomesearch.object.InteractomeSearchResult;
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
	
	private static String pathPrefix = "/opt/ndex/data/";
/*	private boolean usingOldVisualPropertyAspect;
	private int edgeLimit;
	private boolean errorOverLimit;
	private boolean directOnly;
	private String searchTerms; */
	private GeneSymbolIndexer symbolDB;
	
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

			String netUUIDStr = symbolDB.getUUIDFromNetId(e.getKey());
	
			if (netUUIDStr == null)
				throw new NdexException("Network id "+ e.getKey() + " is not found in UUID mapping table." );

			Hashtable<String,Object> status = new Hashtable<>();
			status.put("status",  SearchStatus.processing);
			status.put(PROGRESS, 0);
			st.getSources().put(netUUIDStr,status);

			if ( e.getValue().getNodes().size() > 0) {
			//neighbourhoodQuery(taskId, e.getKey(), e.getValue().getNodes(), genes);
				resultList.add(directQuery(taskId, netUUIDStr, e.getValue().getNodes(), genes, status, e.getValue().getHitGenes()));
			}
			//update the status record	
			status.put(PROGRESS, 100);
			status.put("status",  SearchStatus.complete);
			counter += 1.0;
	        st.setProgress(Math.round(counter*100/total));
		}
        
        //TODO: sort the results and then write out to file.
        
        Collections.sort(resultList, new Comparator<InteractomeSearchResult>() {
            @Override
            public int compare(InteractomeSearchResult h1, InteractomeSearchResult h2) {
            	
            	// sorting by score. value = edgeCount*2 + nodeCount
            	int s1 = h1.getSummary().getEdgeCount() *2 + h2.getSummary().getNodeCount();
            	int s2 = h2.getSummary().getEdgeCount() *2 + h2.getSummary().getNodeCount();
            	
            	if (s1>s2) return -1;
            	if (s1 < s2 ) return 1;
            	if (s1 == s2) {
            		if (h1.getSummary().getParentEdgeCount() > h2.getSummary().getParentEdgeCount() ) 
            			return -1;
            		if (h1.getSummary().getParentEdgeCount() < h2.getSummary().getParentEdgeCount() ) 
            		    return 1;
            	}
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

	
	private static InteractomeSearchResult directQuery(UUID taskId, String netUUIDStr, final Set<Long> nodeIds, List<String> genes,
			Hashtable<String,Object> status, Set<String> hitgenes) throws IOException, NdexException {
		long t1 = Calendar.getInstance().getTimeInMillis();
		Set<Long> edgeIds = new TreeSet<> ();
		
		InteractomeSearchResult currentResult = new  InteractomeSearchResult();
		currentResult.setNetworkUUID(netUUIDStr);
		currentResult.setHitGenes(hitgenes);
		InteractomeResultNetworkSummary s = new InteractomeResultNetworkSummary();
		currentResult.setSummary(s);
		s.setParentEdgeCount(App.getDBTable().get(netUUIDStr).getEdgeCount());
		s.setParentNodeCount(App.getDBTable().get(netUUIDStr).getNodeCount());
		s.setNodeCount(nodeIds.size());
		
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
								if (!nodeIds.contains(edge.getSource()))
									nodeIds.add(edge.getSource());
								if ( !nodeIds.contains(edge.getTarget()))
									nodeIds.add(edge.getTarget());
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
			s.setEdgeCount(edgeIds.size());

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
		if (md.getMetaDataElement(NodeAttributesElement.ASPECT_NAME) != null) {
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
	
}
