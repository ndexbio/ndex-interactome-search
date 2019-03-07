package org.ndexbio.interactomesearch.client;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.ndexbio.interactomesearch.object.InteractomeRefNetworkEntry;
import org.ndexbio.interactomesearch.object.InteractomeSearchResult;
import org.ndexbio.interactomesearch.object.SearchStatus;
import org.ndexbio.model.exceptions.NdexException;

public class IneractomeRestClientTest {

	@Test
	public void test() throws NdexException {
		InteractomeRestClient client = new InteractomeRestClient("http://localhost:8285/interactome/v1/", null);
		
	    List<InteractomeRefNetworkEntry> r = client.getDatabase();
	    
		assertEquals(r.size(), 5);

	}

	@Test
	public void test1() throws NdexException, IOException {
		InteractomeRestClient client = new InteractomeRestClient("http://localhost:8285/interactome/v1/", null);
		
		String[] genes = {"GAB1", "FOS", "SRF", "AATF", "AKT1", "FOOOO", "BARRR"};
		
		UUID id = client.search(Arrays.asList(genes));
		
		assertEquals(id, UUID.fromString("fba34524-54f0-3eca-9d94-d1e76e9f7235") );
		
		SearchStatus status = client.getSearchStatus(id);
		
		assertTrue (status != null);
		
		List<InteractomeSearchResult> r = client.getSearchResult(id);
		
		assertEquals(r.size(), 5);
	
		try (InputStream inputStream = client.getOverlayedNetworkStream(id, UUID.fromString(r.get(0).getNetworkUUID()))) { 
			String result = IOUtils.toString(inputStream, StandardCharsets.UTF_8);
			assertTrue ( result.length()>20);
		}
		
		
	}
	
}
