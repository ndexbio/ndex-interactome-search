package org.ndexbio.interactomesearch;

import static org.junit.Assert.*;

import java.io.IOException;
import java.sql.SQLException;
import java.util.UUID;

import org.junit.Test;
import org.ndexbio.interactomesearch.GeneSymbolIndexer;

import com.fasterxml.jackson.core.JsonProcessingException;

public class DBTests {

	@Test
	public void test() throws SQLException, JsonProcessingException, IOException {
		
		//tryout the implementation.
	//	GeneSymbolIndexer db = new GeneSymbolIndexer("/opt/ndex/services/interactome/genedb");
		
	//	db.rebuildIndex(UUID.fromString("674fb45b-3eda-11e9-a315-96880dd7c540"),"a", "http://www.home.ndexbio.org/img/pid-logo-ndex.jpg");
			
	//	db.rebuildIndex(UUID.fromString("3c1bdc9e-2be3-11e9-ad04-52495394a1cd"),"a", "http://www.home.ndexbio.org/img/pid-logo-ndex.jpg");
		
	//	db.rebuildIndex(UUID.fromString("c81250d8-92b5-11e9-aca1-025460cf6cb8"),"a", "http://www.home.ndexbio.org/img/pid-logo-ndex.jpg");

	//	db.rebuildIndex(UUID.fromString("38e0d5d9-2be3-11e9-ad04-52495394a1cd"),"i");
	
	//	db.rebuildIndex(UUID.fromString("383bbc37-2be3-11e9-ad04-52495394a1cd"),"i");

	//	db.shutdown();
	}

}
