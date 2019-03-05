package org.ndexbio.interactomesearch.object;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)

public class InteractomeSearchResult {
	private int rank;
	private Set<String> hitGenes;
	private InteractomeResultNetworkSummary summary;
	private String networkUUID;

	public InteractomeSearchResult() {
		summary = new InteractomeResultNetworkSummary();
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
