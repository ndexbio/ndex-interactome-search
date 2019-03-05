package org.ndexbio;

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
		GeneSymbolIndexer db = new GeneSymbolIndexer("/opt/ndex/services/interactome/genedb" );
		
		db.rebuildIndex(UUID.fromString("3c1bdc9e-2be3-11e9-ad04-52495394a1cd"));
			
		db.rebuildIndex(UUID.fromString("3affe54b-2be3-11e9-ad04-52495394a1cd"));
		
		db.rebuildIndex(UUID.fromString("7b54eed4-317c-11e9-8c1d-76b6f4944e63"));

		db.rebuildIndex(UUID.fromString("38e0d5d9-2be3-11e9-ad04-52495394a1cd"));
	
		db.rebuildIndex(UUID.fromString("383bbc37-2be3-11e9-ad04-52495394a1cd"));

		db.shutdown();
	}

}
