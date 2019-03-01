/**
 * Copyright (c) 2013, 2016, The Regents of the University of California, The Cytoscape Consortium
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */
package org.ndexbio;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocumentList;
import org.ndexbio.model.exceptions.BadRequestException;
import org.ndexbio.model.exceptions.NdexException;

public class SingleNetworkSolrIdxManager implements AutoCloseable{

	private String solrUrl;
	
	private String collectionName; 
	private HttpSolrClient client;
		
	public static final String ID = "id";

		
	public SingleNetworkSolrIdxManager(String networkUUID) {
		collectionName = networkUUID;
		solrUrl = "http://localhost:8983/solr";
		client = new HttpSolrClient.Builder(solrUrl).build();
	}
	
	protected static NdexException convertException(HttpSolrClient.RemoteSolrException e, String core_name) {
		if (e.code() == 400) {
			String err = e.getMessage();
			Pattern p = Pattern.compile("Error from server at .*/" + core_name +": (.*)");
			Matcher m = p.matcher(e.getMessage());
			if ( m.matches()) {
				err = m.group(1);
			} 
			return new BadRequestException(err);
		}	
		return new NdexException("Error from NDEx Solr server: " + e.getMessage());
	}
	
	public SolrDocumentList getNodeIdsByQuery(String query, int limit) throws SolrServerException, IOException, NdexException {
		client.setBaseURL(solrUrl+ "/" + collectionName);

		SolrQuery solrQuery = new SolrQuery();
		
		solrQuery.setQuery(query).setFields(ID);
		solrQuery.setStart(0);
		if (limit >0)
			solrQuery.setRows(limit);
		else 
			solrQuery.setRows(30000000);
		
		try {
			QueryResponse rsp = client.query(solrQuery);
			SolrDocumentList dds = rsp.getResults();
			return dds;
		} catch (HttpSolrClient.RemoteSolrException e) {
			throw convertException(e, collectionName);
		}
	}
	
	
	public void close () {
		try {
			client.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
