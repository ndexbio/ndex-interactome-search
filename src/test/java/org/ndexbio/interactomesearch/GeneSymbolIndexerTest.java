package org.ndexbio.interactomesearch;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import org.h2.jdbcx.JdbcConnectionPool;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.rules.TemporaryFolder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.ndexbio.interactomesearch.object.NetworkShortSummary;
import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.rest.client.NdexRestClient;
import org.ndexbio.rest.client.NdexRestClientModelAccessLayer;

import com.fasterxml.jackson.core.JsonProcessingException;



@ExtendWith(MockitoExtension.class)

class GeneSymbolIndexerTest {

	@Mock 
	NdexRestClient ndexClient;
	
	@Mock
	NdexRestClientModelAccessLayer ndex;
	
	 @Rule
	 public TemporaryFolder tempFolder = new TemporaryFolder();
	
	@Test
	void test() throws JsonProcessingException, SQLException, IOException, NdexException {
		
		tempFolder.create();
		String dbPath = tempFolder.getRoot().getAbsolutePath() + "/testdb";
		JdbcConnectionPool cplocal = JdbcConnectionPool.create("jdbc:h2:" + dbPath, "sa", "sa");
	
		NetworkSummary s = new NetworkSummary();
		s.setEdgeCount(0);
		
		UUID networkId = UUID.fromString("000b522c-dca0-11e8-aaa6-0ac135e8bacf"); 
		
		when ( ndex.getNetworkSummaryById(networkId)).thenReturn(s);
		
		GeneSymbolIndexer db = new GeneSymbolIndexer(cplocal, "i", ndex);
		db.setPathPrefix(".");
		db.rebuildIndex(networkId, null);

	    try ( Connection conn = cplocal.getConnection()) {
	      	        try (PreparedStatement p = conn.prepareStatement("select 1 from NETWORKS_i where NET_UUID ='" + networkId +"'")) {
				try ( ResultSet rs = p.executeQuery()) {
					assertFalse( rs.next());
				}
			}
	    }
	
	}

}
