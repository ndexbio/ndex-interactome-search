package org.ndexbio.interactomesearch.object;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import org.ndexbio.model.exceptions.NdexException;
import org.ndexbio.model.object.network.NetworkSummary;
import org.ndexbio.rest.client.NdexRestClientModelAccessLayer;

public class InteractomeNetworkSet {
	
	private Set<InteractomeNetworkEntry> ppiNetworks;
	private Set<InteractomeNetworkEntry> associationNetworks;
	
	public InteractomeNetworkSet() {
		setPpiNetworks(new TreeSet<>()); 
		this.setAssociationNetworks(new TreeSet<>());
	}

	public Set<InteractomeNetworkEntry> getPpiNetworks() {
		return ppiNetworks;
	}

	public void setPpiNetworks(Set<InteractomeNetworkEntry> ppiNetworks) {
		this.ppiNetworks = ppiNetworks;
	}

	public Set<InteractomeNetworkEntry> getAssociationNetworks() {
		return associationNetworks;
	}

	public void setAssociationNetworks(Set<InteractomeNetworkEntry> associationNetworks) {
		this.associationNetworks = associationNetworks;
	}
}
