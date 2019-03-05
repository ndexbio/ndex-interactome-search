package org.ndexbio.interactomesearch;

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
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.h2.jdbcx.JdbcConnectionPool;
import org.ndexbio.cxio.aspects.datamodels.NodeAttributesElement;
import org.ndexbio.cxio.aspects.datamodels.NodesElement;
import org.ndexbio.cxio.core.AspectIterator;
import org.ndexbio.interactomesearch.object.GeneSymbolSearchResult;

import com.fasterxml.jackson.core.JsonProcessingException;

public class GeneSymbolIndexer {
	
	//static final String DB_URL = "jdbc:h2:~/test";  
	
	private JdbcConnectionPool cp;
	
	private  String pathPrefix;
	
	private  TreeMap<Integer, String> netIdMapper;
	   
	public GeneSymbolIndexer(String dbpath) throws SQLException {
		netIdMapper = new TreeMap<>();
		cp = JdbcConnectionPool.create("jdbc:h2:" + dbpath, "sa", "sa");
	    try ( Connection conn = cp.getConnection()) {
	        conn.createStatement().execute("CREATE TABLE  IF NOT EXISTS NETWORKS (NET_ID INT auto_increment PRIMARY KEY, NET_UUID VARCHAR(36) UNIQUE)");
	        conn.createStatement().execute("CREATE TABLE  IF NOT EXISTS GENESYMBOLS (SYMBOL VARCHAR(30),NODE_ID BIGINT, NET_ID INT, "+
	                  "PRIMARY KEY (SYMBOL,NODE_ID,NET_ID), FOREIGN KEY(NET_ID) REFERENCES NETWORKS(NET_ID))");

	        // populate the id mapping table
	        try (PreparedStatement p = conn.prepareStatement("select net_id, net_uuid from networks")) {
				try ( ResultSet rs = p.executeQuery()) {
					while ( rs.next()) {
					  netIdMapper.put(Integer.valueOf(rs.getInt(1)), rs.getString(2));
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
	

	public String getUUIDFromNetId(Integer net_id) { return netIdMapper.get(net_id);}
	
	public Collection<String> getUUIDsFromDB() {
		return netIdMapper.values();
	}
	
	public void rebuildIndex(UUID networkUUID) throws SQLException, JsonProcessingException, IOException {
		
		System.out.println("Rebuild Index on network " + networkUUID);
		
		removeIndex (networkUUID);
		
        int net_id;
		try ( Connection conn = cp.getConnection()) {
	        
	        conn.createStatement().execute("insert into networks (net_uuid) values('"+networkUUID+"')");
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
					if ( type.equals("protein") || type.equals("complex") || 
						 type.equals("gene") || type.equals("proteinfamily")) {
						nodeTable.get(attr.getPropertyOf()).put("t", type);
					} else  // remove this node from table if it doesn't have gene symbol on it.
						nodeTable.remove(attr.getPropertyOf());
				} else if ( attr.getName().equals("members")) {
					HashMap<String,Object> n = nodeTable.get(attr.getPropertyOf());
					if ( n != null) {
						n.put("m", attr.getValues());
					}
				}						
			}
	    } catch (FileNotFoundException e) {
	    	// ignore this aspect if nodes have no attributes on them.
	    }

	    int count = 0;
	    // now create the index
	    try ( Connection conn = cp.getConnection()) {
	    	for(Map.Entry<Long, HashMap<String,Object>> e : nodeTable.entrySet()) {
	    		HashMap<String, Object> n = e.getValue();
	    		String o = (String)n.get("t"); 
	    		if ( o == null || o.equals("protein") || o.equals("gene")) {
	    			String geneSymbol = (String)n.get("n");
	    			if ( geneSymbol != null) {
	    				insertGeneSymbol (conn, geneSymbol, e.getKey(), net_id);
	    				count++;
	    			}
	    		} else if (o.equals("proteinfamily") || o.equals("complex")) {
	    			List<String> geneList = (List<String>)n.get("m");
	    			for ( String g : geneList) {
	    				insertGeneSymbol(conn, g, e.getKey(), net_id);	
	    				count++;
	    			}
	    		}
	    	}
	    	conn.commit();
	    }
	    
	    netIdMapper.put(net_id,networkUUID.toString());
	    System.out.println (count + " genes indexed. Done.");
	}
	
	private static void insertGeneSymbol(Connection conn, String gene,Long nodeId, int netId) throws SQLException {
		
		// quick hack to prevent errors because the networks have not been normalized yet. 
		if ( gene.length()>30 || ( ! gene.matches ("^[^\\(\\)\\s,']+$") )) {
			System.err.println("Wanring: gene symbol '" + gene + "' doesn't look correct, ignoring it.");
			return;
		}
		String sqlStr = "insert into genesymbols (SYMBOL, node_id, net_id) values (?, ?,"+netId + ")";
		try (PreparedStatement pst = conn.prepareStatement(sqlStr)) {
			pst.setString(1, gene);
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
		Optional<Map.Entry<Integer, String>> e =
				netIdMapper.entrySet().stream().filter( r -> r.getValue().equals(networkUUID)).findFirst();
		if ( e.isPresent())
			netIdMapper.remove(e.get().getKey());
		
	}
	
	public GeneSymbolSearchResult search(Collection<String> genes) throws SQLException {
		
		GeneSymbolSearchResult r = new GeneSymbolSearchResult();
		r.initializeResultSet(netIdMapper);
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
	
	
	public static void main(String... args) throws Exception {
		
		if ( args.length == 3 ) {
			GeneSymbolIndexer db = new GeneSymbolIndexer(args[0]);
			db.setPathPrefix(args[1]);
			db.rebuildIndex(UUID.fromString(args[2]));
			db.shutdown();
		} else {
			System.out.println ("Rebuild Index of a network GeneSymbolIndexer <db_path> <network_file_prefix> networkUUID");
			System.out.println ("Example: GeneSymbolIndexer /opt/ndex/services/interactome/genedb /opt/ndex/data/ xxx-xxxxx-xxxxx");	
		
		}
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
