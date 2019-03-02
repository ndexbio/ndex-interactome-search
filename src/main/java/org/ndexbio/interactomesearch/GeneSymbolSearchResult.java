package org.ndexbio.interactomesearch;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)

public class GeneSymbolSearchResult {
   
	@JsonProperty( "hitGenes")
	private Set<String> hitGenes;
	@JsonProperty( "missedGenes")
	private Set<String> missedGenes;
	
	@JsonProperty( "resultSet")
	//key is the network_id, value is an object contains query starting nodes
	private TreeMap<Integer, GeneQueryNodes> resultSet;
	
	public GeneSymbolSearchResult() {
    	hitGenes = new TreeSet<>();
    	missedGenes = new TreeSet<>();
    	
    	resultSet = new TreeMap<>();
    }

	
/*	public GeneSymbolSearchResult(Map<Integer, String> idMapper) {
    	hitGenes = new TreeSet<>();
    	missedGenes = new TreeSet<>();
    	
    	resultSet = new TreeMap<>();
    	idMapper.forEach((k,v) -> resultSet.put(k,new GeneQueryNodes()));
    } */
	
	public void initializeResultSet(Map<Integer, String> idMapper) {
    	idMapper.forEach((k,v) -> resultSet.put(k,new GeneQueryNodes()));
	}

	public Set<String> getHitGenes() {
		return hitGenes;
	}

	public Set<String> getMissedGenes() {
		return missedGenes;
	}
	
	public TreeMap<Integer, GeneQueryNodes> getResultSet() {
		return resultSet;
	}
	
	
	public void addGeneNode(String gene,long nodeId, int netId) {
		hitGenes.add(gene);
		GeneQueryNodes nodes = resultSet.get(netId);
		nodes.getHitGenes().add(gene);
		nodes.getNodes().add(nodeId);
	}
}
