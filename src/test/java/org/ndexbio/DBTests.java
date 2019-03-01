package org.ndexbio;

import static org.junit.Assert.*;

import java.io.IOException;
import java.sql.SQLException;

import org.junit.Test;

import com.fasterxml.jackson.core.JsonProcessingException;

public class DBTests {

	@Test
	public void test() throws SQLException, JsonProcessingException, IOException {
		
		//tryout the implementation.
		GeneSymolIndexer db = new GeneSymolIndexer("/opt/ndex/services/interactome/genedb" );
		
		db.rebuildIndex("3c1bdc9e-2be3-11e9-ad04-52495394a1cd");
			
		db.rebuildIndex("3affe54b-2be3-11e9-ad04-52495394a1cd");
		
		db.rebuildIndex("7b54eed4-317c-11e9-8c1d-76b6f4944e63");

		db.rebuildIndex("38e0d5d9-2be3-11e9-ad04-52495394a1cd");
	
		db.rebuildIndex("383bbc37-2be3-11e9-ad04-52495394a1cd");

		db.shutdown();
	}

}
