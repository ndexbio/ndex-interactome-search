package org.ndexbio.interactomesearch;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

import org.h2.jdbcx.JdbcConnectionPool;
import org.ndexbio.cxio.aspects.datamodels.ATTRIBUTE_DATA_TYPE;
import org.ndexbio.cxio.aspects.datamodels.NetworkAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.cxio.core.AspectIterator;
import org.ndexbio.interactomesearch.object.GeneSymbolSearchResult;
import org.ndexbio.interactomesearch.object.InteractomeNetworkEntry;
import org.ndexbio.interactomesearch.object.InteractomeNetworkSet;
import org.ndexbio.interactomesearch.object.NetworkShortSummary;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.model.tools.TermUtilities;
import org.ndexbio.rest.client.NdexRestClient;
import org.ndexbio.rest.client.NdexRestClientModelAccessLayer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class GeneSymbolIndexer {
	
	//static final String DB_URL = "jdbc:h2:~/test";  
	
	private JdbcConnectionPool cp;
	
	private String pathPrefix;
	
	private String networkType; // should be unique across the application because it is used to as a suffix of table name 
	
	private TreeMap<Integer, NetworkShortSummary> netIdMapper;
	
	NdexRestClientModelAccessLayer ndex; 
	
	/**
	 * 
	 * @param dbpath
	 * @param networkType should be a short alphabetical string. Currently the valid value should only be 
	 * 'i' for PPI networks or 'a' for geneAssociation networks.
	 * @throws SQLException
	 */
	public GeneSymbolIndexer(JdbcConnectionPool cp, String networkType, NdexRestClientModelAccessLayer ndex ) throws SQLException {
		
		this.networkType = networkType;
		
		netIdMapper = new TreeMap<>();
		this.cp = cp;
		
	    try ( Connection conn = cp.getConnection()) {
	        conn.createStatement().execute("CREATE TABLE IF NOT EXISTS NETWORKS_" + networkType + " (NET_ID INT auto_increment PRIMARY KEY, "
	        		+ "NET_UUID VARCHAR(36) UNIQUE, imageurl varchar(500))");
	        conn.createStatement().execute("CREATE TABLE IF NOT EXISTS GENESYMBOLS_" + networkType + " (SYMBOL VARCHAR(30),NODE_ID BIGINT, NET_ID INT, "+
	                  "PRIMARY KEY (SYMBOL,NODE_ID,NET_ID), FOREIGN KEY(NET_ID) REFERENCES NETWORKS_"+ networkType + "(NET_ID))");

	        // populate the id mapping table
	        try (PreparedStatement p = conn.prepareStatement("select net_id, net_uuid,imageurl from networks_" + networkType)) {
				try ( ResultSet rs = p.executeQuery()) {
					while ( rs.next()) {
					  NetworkShortSummary summary = new NetworkShortSummary();
					  summary.setUuid(rs.getString(2));
					  summary.setImageURL(rs.getString(3));
					  netIdMapper.put(Integer.valueOf(rs.getInt(1)), summary );
					}
				}
			}
	    }
	    
	    pathPrefix = NetworkQueryManager.getDataFilePathPrefix();    
	    this.ndex = ndex;
	}
	
	protected void setPathPrefix(String pathPrefix) { this.pathPrefix = pathPrefix;}
	
/*	public void shutdown() {
		cp.dispose();
	} */
	

	//public String getUUIDFromNetId(Integer net_id) { return netIdMapper.get(net_id).getUuid();}

	public NetworkShortSummary getShortSummaryFromNetId(Integer net_id) {return netIdMapper.get(net_id);}
	
	public TreeMap<Integer, NetworkShortSummary> getIdMapper() { return netIdMapper;}
	
	/*public Collection<String> getUUIDsFromDB() {
		return netIdMapper.values();
	} */
	
	/**
	 * 
	 * @param networkUUID
	 * @param type value 'i' means interaction network. 'a' means protein association network. They will be treated differently in the search.
	 * @param imageURL 
	 * @throws SQLException
	 * @throws JsonProcessingException
	 * @throws IOException
	 * @throws NdexException 
	 */
	public void rebuildIndex(UUID networkUUID, String imageURL) throws SQLException, JsonProcessingException, IOException, NdexException {
		
		System.out.println("Rebuild Index on network " + networkUUID);
		
		removeIndex (networkUUID);

		NetworkSummary s = ndex.getNetworkSummaryById(networkUUID);
		if ( s.getEdgeCount() == 0 ) {
			System.err.println("Network " + networkUUID.toString() + " has 0 edges, it will not be indexed.");
			return;
		}	
        int net_id;
		try ( Connection conn = cp.getConnection()) {
	        
	        String sqlStr = "insert into networks_" + networkType + " (net_uuid, imageurl) values('"+networkUUID+"',?)";
			try (PreparedStatement pst = conn.prepareStatement(sqlStr)) {
				pst.setString(1,imageURL);
				pst.executeUpdate();
			}
	        conn.commit();
	        try (ResultSet r = conn.createStatement().executeQuery("select net_id from networks_" + networkType + " where net_uuid='"+networkUUID+"'")) {
	        	if ( r.next()) {
	        		net_id = r.getInt(1);
	        	} else {
	        		throw new SQLException ("Can't get net_id after insert an new network entry in networks table.");
	        	}
	        }
	        System.out.println("Network " + networkUUID + " is added to net_id table.");
		}    
	    
		/* 
	     *  nodeTable key is the node Id. Its value a record of the node that we are goint to index on. 
	     *  It has these fields:
	     *    "n" -- node name, suppose to be gene symbol
	     *    "t" -- node type, its value should be one of these protein, complex, gene, proteinfamily
	     *    "m" -- member, a list of gene symbol, only exists when the type is complex or proteinfamily.
	     */
	    TreeMap<Long, HashMap<String,Object>> nodeTable = new TreeMap<> ();
	    String netIDStr = networkUUID.toString();
	        	
	    try (AspectIterator<NodesElement> ni = new AspectIterator<>( netIDStr,NodesElement.ASPECT_NAME, NodesElement.class, pathPrefix))  {
			while (ni.hasNext()) {
				NodesElement node = ni.next();
                HashMap<String,Object> n = new HashMap<>();
                n.put("n", node.getNodeName());
                nodeTable.put(node.getId(), n); 
			}
	    }
	        	
	    try (AspectIterator<NodeAttributesElement> ni = new AspectIterator<>( netIDStr,NodeAttributesElement.ASPECT_NAME,
	        			     NodeAttributesElement.class, pathPrefix))  {
			while (ni.hasNext()) {
				NodeAttributesElement attr = ni.next();
				if ( attr.getName().equals("type")) {
					String type = attr.getValue();
					if ( type != null) {
						type = type.toLowerCase(); 
						if ( type.equals("protein") || type.equals("complex") || 
							type.equals("gene") || type.equals("proteinfamily")) {
							nodeTable.get(attr.getPropertyOf()).put("t", type);
						} else  // remove this node from table if it doesn't have gene symbol on it.
							nodeTable.remove(attr.getPropertyOf());
					} 
				} else if ( attr.getName().equals("member")) {
					HashMap<String,Object> n = nodeTable.get(attr.getPropertyOf());
					if ( n != null) {
						n.put("m", attr.getValues());
					}
				}						
			}
	    } catch (FileNotFoundException e) {
	    	// ignore this aspect if nodes have no attributes on them.
	    }

	    System.out.println ("Total " + nodeTable.size() + " nodes after first scan.");

	    List<String> netTypes = getNetworkTypes(netIDStr);
	    boolean isAssociationNetwork = netTypes != null && 
	    		(netTypes.contains("geneassocation") || netTypes.contains("proteinassociation"));
	    
	    if ( isAssociationNetwork || (networkType != null && networkType.equals("a"))) {   // non-typed node in association network
	    	nodeTable.entrySet() 
            .removeIf( 
                entry -> (entry.getValue().get("t") == null));
		}
	    
	    System.out.println ("Total " + nodeTable.size() + " nodes for indexing.");
	    
	    int count = 0;
	    // now create the index
	    try ( Connection conn = cp.getConnection()) {
	    	for(Map.Entry<Long, HashMap<String,Object>> e : nodeTable.entrySet()) {
	    		HashMap<String, Object> n = e.getValue();
	    		String o = ((String)n.get("t"));
	    		if ( o == null || o.equalsIgnoreCase("protein") || o.equalsIgnoreCase("gene")) {
	    			String geneSymbol = (String)n.get("n");
	    			if ( geneSymbol != null) {
	    				insertGeneSymbol (conn, geneSymbol, e.getKey(), net_id);
	    				count++;
	    			}
	    		} else if (o.equalsIgnoreCase("proteinfamily") || o.equalsIgnoreCase("complex")) {
	    			List<String> geneList = (List<String>)n.get("m");
	    			if ( geneList != null) {
	    				for ( String g : geneList) {
	    					insertGeneSymbol(conn, getIndexableString(g), e.getKey(), net_id);	
	    					count++;
	    				}
	    			}
	    		}
	    	}
	    	conn.commit();
	    }
	    
	    NetworkShortSummary summary = new NetworkShortSummary();
	    summary.setUuid(networkUUID.toString());
	    summary.setType(networkType);
	    netIdMapper.put(net_id,summary);
	    System.out.println (count + " genes indexed. Done.");
	}
	
	private void insertGeneSymbol(Connection conn, String gene,Long nodeId, int netId) throws SQLException {
		
		// quick hack to prevent errors because the networks have not been normalized yet. 
		if ( gene.length()>30 || ( ! gene.matches ("^[^\\(\\)\\s,']+$") )) {
			System.err.println("Warning: gene symbol '" + gene + "' doesn't look correct, ignoring it.");
			return;
		}
		String sqlStr = "insert into genesymbols_" + networkType + " (SYMBOL, node_id, net_id) values (?, ?,"+netId + ")";
		try (PreparedStatement pst = conn.prepareStatement(sqlStr)) {
			pst.setString(1, gene.toUpperCase());
			pst.setLong(2, nodeId);
			pst.executeUpdate();
		}			
	}
	
	public void removeIndex(UUID networkUUID) throws SQLException {
		try ( Connection conn = cp.getConnection()) {
	        conn.createStatement().execute("delete from genesymbols_"+networkType + " where net_id =(select net_id from networks_"+networkType + " where net_uuid='"+networkUUID+"')");
	        
	        conn.createStatement().execute("delete from NETWORKS_"+networkType + " where NET_UUID ='"+networkUUID+"'");
	        conn.commit();
	    }	
		Optional<Map.Entry<Integer, NetworkShortSummary>> e =
				netIdMapper.entrySet().stream().filter( r -> r.getValue().getUuid().equals(networkUUID)).findFirst();
		if ( e.isPresent())
			netIdMapper.remove(e.get().getKey());
		
	}
	
	public void removeAllIndexes() throws SQLException {
		try ( Connection conn = cp.getConnection()) {
	        conn.createStatement().execute("delete from genesymbols_"+networkType );
	        
	        conn.createStatement().execute("delete from NETWORKS_"+networkType);
	        conn.commit();
	    }	
		netIdMapper.clear();
		
	}
	
	public GeneSymbolSearchResult search(Collection<String> genes) throws SQLException {
		
		GeneSymbolSearchResult r = new GeneSymbolSearchResult();
		//r.initializeResultSet(netIdMapper);
		String sqlStr = " select symbol,node_id,net_id from GENESYMBOLS_" + networkType +" n where symbol in("+ concatenateGenes(genes) + ")";
		
		try ( Connection conn = cp.getConnection()) {
			try (PreparedStatement p = conn.prepareStatement(sqlStr)) {
				try ( ResultSet rs = p.executeQuery()) {
					while ( rs.next()) {
						String gene = rs.getString(1);
						long nodeId = rs.getLong(2);
						int netId = rs.getInt(3);
						r.addGeneNode(gene,nodeId,netId);
					}
				}
			}
		}
		
		genes.stream().filter( g -> !r.getHitGenes().contains(g)).forEach( g -> r.getMissedGenes().add(g));
		return r;
	} 
	
	
	private List<String> getNetworkTypes (String networkIdStr) throws JsonProcessingException, IOException {
	    try (AspectIterator<NetworkAttributesElement> ni = new AspectIterator<>( networkIdStr,NetworkAttributesElement.ASPECT_NAME, NetworkAttributesElement.class, pathPrefix))  {
			while (ni.hasNext()) {
				NetworkAttributesElement attr = ni.next();
				if ( attr.getName().equals("networkType") && attr.getDataType() == ATTRIBUTE_DATA_TYPE.LIST_OF_STRING) {
					return attr.getValues();
				}
			}
	    }
	    return null;
	}
	
	/**
	 * 
	 * @param args takes network list file as the last parameter. The file is a json file with this format 
	 *   [ 
	 *   { "uuid": ".....", "type": 'i'/'a', "imageurl": "http://...." },
	 *   ...
	 *   ]
	 *   assume it is in the current working directory.
	 * @throws Exception
	 */
	public static void main(String... args) throws Exception {
		
		if ( args.length == 4 ) {
				
			try (FileInputStream inputStream = new FileInputStream(args[2])) {
				  //   Type sooper = getClass().getGenericSuperclass();
				ObjectMapper om = new ObjectMapper();
				InteractomeNetworkSet dataSet = om.readValue(inputStream, InteractomeNetworkSet.class);
				
				String dbPath = args[0];
				JdbcConnectionPool cplocal = JdbcConnectionPool.create("jdbc:h2:" + dbPath, "sa", "sa");
				
				NdexRestClientModelAccessLayer ndex = new NdexRestClientModelAccessLayer(
						new NdexRestClient(null, null, args[3]));

				GeneSymbolIndexer db1 = new GeneSymbolIndexer(cplocal, "i", ndex);     
				db1.removeAllIndexes();
				db1.setPathPrefix(args[1]);
				for (InteractomeNetworkEntry entry: dataSet.getPpiNetworks()) {
					db1.rebuildIndex(UUID.fromString(entry.getUuid()), entry.getImageURL());
				}				
				
				GeneSymbolIndexer db2 = new GeneSymbolIndexer(cplocal,"a", ndex);
				db1.removeAllIndexes();
				db2.setPathPrefix(args[1]);
				for (InteractomeNetworkEntry entry: dataSet.getAssociationNetworks()) {
					db2.rebuildIndex(UUID.fromString(entry.getUuid()), entry.getImageURL());
				}

			}
				
		} else if (args.length == 6) {
			String dbPath = args[0];
			JdbcConnectionPool cplocal = JdbcConnectionPool.create("jdbc:h2:" + dbPath, "sa", "sa");
			
			NdexRestClientModelAccessLayer ndex = new NdexRestClientModelAccessLayer(
					new NdexRestClient(null, null, args[5]));
			GeneSymbolIndexer db = new GeneSymbolIndexer(cplocal, args[3], ndex);
			db.setPathPrefix(args[1]);
			db.rebuildIndex(UUID.fromString(args[2]), args[4]);
		//	db.shutdown();
			
		} else {
			
			System.out.println ("Rebuild Index of a network GeneSymbolIndexer <db_path> <network_file_prefix> <network_list_file> <ndex_server_host_name>");
			System.out.println ("Rebuild Index of a network GeneSymbolIndexer <db_path> <network_file_prefix> networkUUID <type> <image_url> <ndex_server_host_name>");
			System.out.println ("Example: GeneSymbolIndexer /opt/ndex/services/interactome/genedb /opt/ndex/data/ xxx-xxxxx-xxxxx i http://example.com/image1.svg");	
			System.out.println ("Note: Rebuilding the index from a network list file, all existing indexes will be cleared.");	
		
		}
    }
	
	private static String getIndexableString(String termString) {
		
		// case 1 : termString is a URI
		// example: http://identifiers.org/uniprot/P19838
		// treat the last element in the URI as the identifier and the rest as
		// prefix string. Just to help the future indexing.
		//
	   // List<String> result = new ArrayList<>(2) ;
		//String identifier = null;
		
		String[] termStringComponents = TermUtilities.getNdexQName(termString);
		if (termStringComponents != null && termStringComponents.length == 2) {
			// case 2: termString is of the form (NamespacePrefix:)*Identifier
	//		if ( !termStringComponents[0].contains(" "))
			//  result.add(termString);
			return (termStringComponents[1]);
			///return  result;
		} 
		
		// case 3: termString cannot be parsed, use it as the identifier.
		// so leave the prefix as null and return the string
		return (termString);
		//return result;
			
	}
	
	
	private static String concatenateGenes(Collection<String> genes) {
		StringBuffer cnd = new StringBuffer() ;
		for ( String idstr : genes ) {
			if (cnd.length()>1)
				cnd.append(',');
			cnd.append('\'');
			cnd.append(idstr.replaceAll("'", "''"));
			cnd.append('\'');			
		}
		return cnd.toString();
	}
}
