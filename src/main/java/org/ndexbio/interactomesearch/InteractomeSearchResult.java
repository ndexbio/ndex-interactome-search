package org.ndexbio.interactomesearch;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)

public class InteractomeSearchResult {
	private int rank;
	private List<String> hitGenes;
	private InteractomeResultNetworkSummary summary;
	private String networkUUID;

	public InteractomeSearchResult() {
		summary = new InteractomeResultNetworkSummary();
		hitGenes = new ArrayList<>(50);
	}

	public int getRank() {
		return rank;
	}

	public void setRank(int rank) {
		this.rank = rank;
	}

	public List<String> getHitGenes() {
		return hitGenes;
	}

	public void setHitGenes(List<String> hitGenes) {
		this.hitGenes = hitGenes;
	}

	public InteractomeResultNetworkSummary getSummary() {
		return summary;
	}

	public void setSummary(InteractomeResultNetworkSummary summary) {
		this.summary = summary;
	}

	public String getNetworkUUID() {
		return networkUUID;
	}

	public void setNetworkUUID(String networkUUID) {
		this.networkUUID = networkUUID;
	}
}
