package org.ndexbio.interactomesearch.object;

import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)

public class InteractomeSearchResult {
	private int rank;
	private Set<String> hitGenes;
	//private InteractomeResultNetworkSummary summary;
	private String networkUUID;
	private int percentOverlap;
	private String description;
	private int nodeCount;
	private int edgeCount;
    private String imageURL;
    
	public InteractomeSearchResult() {
		//summary = new InteractomeResultNetworkSummary();
		hitGenes = new TreeSet<>();
	}

	public int getRank() {
		return rank;
	}

	public void setRank(int rank) {
		this.rank = rank;
	}

	public Set<String> getHitGenes() {
		return hitGenes;
	}

	public void setHitGenes(Set<String> hitGenes) {
		this.hitGenes = hitGenes;
	}
/*
	public InteractomeResultNetworkSummary getSummary() {
		return summary;
	}

	public void setSummary(InteractomeResultNetworkSummary summary) {
		this.summary = summary;
	}
*/
	public String getNetworkUUID() {
		return networkUUID;
	}

	public void setNetworkUUID(String networkUUID) {
		this.networkUUID = networkUUID;
	}

	public int getPercentOverlap() {
		return percentOverlap;
	}

	public void setPercentOverlap(int percentOverlap) {
		this.percentOverlap = percentOverlap;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public int getNodeCount() {
		return nodeCount;
	}

	public void setNodeCount(int nodeCount) {
		this.nodeCount = nodeCount;
	}

	public int getEdgeCount() {
		return edgeCount;
	}

	public void setEdgeCount(int edgeCount) {
		this.edgeCount = edgeCount;
	}

	public String getImageURL() {
		return imageURL;
	}

	public void setImageURL(String imageurl) {
		this.imageURL = imageurl;
	}
}
