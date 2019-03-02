package org.ndexbio;

import static org.junit.Assert.*;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.Test;
import org.ndexbio.interactomesearch.GeneSymbolSearchResult;
import org.ndexbio.interactomesearch.GeneSymolIndexer;

public class DBQueryTests {

	@Test
	public void test() throws SQLException {
		GeneSymolIndexer db = new GeneSymolIndexer("/opt/ndex/services/interactome/genedb");
		
		String[] myArray = { "GAB1", "FOS", "SRF", "AATF", "AKT1", "FOOOO", "BARRR" };
		List<String> mylist = Arrays.asList(myArray);
		System.out.println("full string: " + mylist.stream().collect(Collectors.joining(",")));
		GeneSymbolSearchResult r = db.search(mylist);
		
		System.out.println(r);
		
        db.shutdown();	}
	
	
	@Test
	public void uuidtest () throws InterruptedException {
         String foo = "akt1 braf kras fooo barrr gaba fos srf aatr";
         String foo2 = "akt1 braf kras fooo barrr gaba fos srf aatr foxo egfr";
         UUID u = UUID.nameUUIDFromBytes(foo.getBytes());
         System.out.println(u);
         u = UUID.nameUUIDFromBytes(foo2.getBytes());
         System.out.println(u);
         Thread.sleep(300);
         System.out.println(UUID.nameUUIDFromBytes(foo.getBytes()));
         Thread.sleep(300);
         System.out.println(UUID.nameUUIDFromBytes(foo.getBytes()));
         
         
	}

}
