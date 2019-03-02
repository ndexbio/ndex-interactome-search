package org.ndexbio.interactomesearch;

import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)

public class GeneQueryNodes {
	
	// node iDs that relate to the query genes
	private Set<Long> nodes;
	
	//query genes that are found in this network.
	private Set<String> hits;
	
	public GeneQueryNodes() {
		nodes = new TreeSet<>();
		hits = new TreeSet<>();	
	}
	
	public Set<Long> getNodes() {
		return nodes;
	}
	
	public void setNodes(Set<Long> nodes) {
		this.nodes = nodes;
	}
	
	public void setHits(Set<String> hits) {
		this.hits = hits;
	}
	
	public Set<String> getHitGenes() {
		return hits;
	}
}
