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
import java.util.Hashtable;
import java.util.Iterator;
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
import org.ndexbio.interactomesearch.object.NetworkShortSummary;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.tools.TermUtilities;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

public class GeneSymbolIndexer {
	
	//static final String DB_URL = "jdbc:h2:~/test";  
	
	private JdbcConnectionPool cp;
	
	private String pathPrefix;
	
	private TreeMap<Integer, NetworkShortSummary> netIdMapper;
		   
	public GeneSymbolIndexer(String dbpath) throws SQLException {
		netIdMapper = new TreeMap<>();
		cp = JdbcConnectionPool.create("jdbc:h2:" + dbpath, "sa", "sa");
	    try ( Connection conn = cp.getConnection()) {
	        conn.createStatement().execute("CREATE TABLE IF NOT EXISTS NETWORKS (NET_ID INT auto_increment PRIMARY KEY, "
	        		+ "NET_UUID VARCHAR(36) UNIQUE, type varchar(10), imageurl varchar(500))");
	        conn.createStatement().execute("CREATE TABLE IF NOT EXISTS GENESYMBOLS (SYMBOL VARCHAR(30),NODE_ID BIGINT, NET_ID INT, "+
	                  "PRIMARY KEY (SYMBOL,NODE_ID,NET_ID), FOREIGN KEY(NET_ID) REFERENCES NETWORKS(NET_ID))");

	        // populate the id mapping table
	        try (PreparedStatement p = conn.prepareStatement("select net_id, net_uuid, type, imageurl from networks")) {
				try ( ResultSet rs = p.executeQuery()) {
					while ( rs.next()) {
					  NetworkShortSummary summary = new NetworkShortSummary();
					  summary.setUuid(rs.getString(2));
					  summary.setType(rs.getString(3));
					  summary.setImageURL(rs.getString(4));
					  netIdMapper.put(Integer.valueOf(rs.getInt(1)), summary );
					}
				}
			}
	    }
	    
	    pathPrefix = NetworkQueryManager.getDataFilePathPrefix();    
	}
	
	private void setPathPrefix(String pathPrefix) { this.pathPrefix = pathPrefix;}
	
	public void shutdown() {
		cp.dispose();
	}
	

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
	 */
	public void rebuildIndex(UUID networkUUID, String networkType, String imageURL) throws SQLException, JsonProcessingException, IOException {
		
		System.out.println("Rebuild Index on network " + networkUUID);
		
		removeIndex (networkUUID);
		
        int net_id;
		try ( Connection conn = cp.getConnection()) {
	        
	        String sqlStr = "insert into networks (net_uuid, type, imageurl) values('"+networkUUID+"',?,?)";
			try (PreparedStatement pst = conn.prepareStatement(sqlStr)) {
				pst.setString(1,networkType);
				pst.setString(2,imageURL);
				pst.executeUpdate();
			}
	        conn.commit();
	        try (ResultSet r = conn.createStatement().executeQuery("select net_id from networks where net_uuid='"+networkUUID+"'")) {
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
	
	private static void insertGeneSymbol(Connection conn, String gene,Long nodeId, int netId) throws SQLException {
		
		// quick hack to prevent errors because the networks have not been normalized yet. 
		if ( gene.length()>30 || ( ! gene.matches ("^[^\\(\\)\\s,']+$") )) {
			System.err.println("Warning: gene symbol '" + gene + "' doesn't look correct, ignoring it.");
			return;
		}
		String sqlStr = "insert into genesymbols (SYMBOL, node_id, net_id) values (?, ?,"+netId + ")";
		try (PreparedStatement pst = conn.prepareStatement(sqlStr)) {
			pst.setString(1, gene.toUpperCase());
			pst.setLong(2, nodeId);
			pst.executeUpdate();
		}			
	}
	
	public void removeIndex(UUID networkUUID) throws SQLException {
		try ( Connection conn = cp.getConnection()) {
	        conn.createStatement().execute("delete from genesymbols where net_id =(select net_id from networks where net_uuid='"+networkUUID+"')");
	        
	        conn.createStatement().execute("delete from NETWORKS where NET_UUID ='"+networkUUID+"'");
	        conn.commit();
	    }	
		Optional<Map.Entry<Integer, NetworkShortSummary>> e =
				netIdMapper.entrySet().stream().filter( r -> r.getValue().getUuid().equals(networkUUID)).findFirst();
		if ( e.isPresent())
			netIdMapper.remove(e.get().getKey());
		
	}
	
	public GeneSymbolSearchResult search(Collection<String> genes) throws SQLException {
		
		GeneSymbolSearchResult r = new GeneSymbolSearchResult();
		//r.initializeResultSet(netIdMapper);
		String sqlStr = " select symbol,node_id,net_id from GENESYMBOLS n where symbol in("+ concatenateGenes(genes) + ")";
		
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
		
		if ( args.length == 3 ) {
			GeneSymbolIndexer db = new GeneSymbolIndexer(args[0]);
			db.setPathPrefix(args[1]);
				
			try (FileInputStream inputStream = new FileInputStream(args[2])) {
				  //   Type sooper = getClass().getGenericSuperclass();
				     
				Iterator<Hashtable<String,String>> it = new ObjectMapper().readerFor(TypeFactory.defaultInstance().constructMapLikeType(Hashtable.class,String.class, String.class))
							.readValues(inputStream);
				
				while(it.hasNext()) {
					Hashtable<String,String> s = it.next();
					db.rebuildIndex(UUID.fromString(s.get("uuid")), s.get("type"), s.get("imageURL"));
				}
			}
				
			db.shutdown();
		} else if (args.length == 5) {
			GeneSymbolIndexer db = new GeneSymbolIndexer(args[0]);
			db.setPathPrefix(args[1]);
			db.rebuildIndex(UUID.fromString(args[2]), args[3], args[4]);
			db.shutdown();
			
		} else {
			
			System.out.println ("Rebuild Index of a network GeneSymbolIndexer <db_path> <network_file_prefix> <network_list_file>");
			System.out.println ("Rebuild Index of a network GeneSymbolIndexer <db_path> <network_file_prefix> networkUUID <type> <image_url>");
			System.out.println ("Example: GeneSymbolIndexer /opt/ndex/services/interactome/genedb /opt/ndex/data/ xxx-xxxxx-xxxxx i http://example.com/image1.svg");	
		
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
