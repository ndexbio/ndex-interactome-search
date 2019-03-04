package org.ndexbio.interactomesearch.object;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)

public class InteractomeResultNetworkSummary {
	private int edgeCount;
	private int nodeCount;
	private long parentNodeCount;
	private long parentEdgeCount;
	
	public InteractomeResultNetworkSummary() {}

	public int getEdgeCount() {
		return edgeCount;
	}

	public void setEdgeCount(int edgeCount) {
		this.edgeCount = edgeCount;
	}

	public int getNodeCount() {
		return nodeCount;
	}

	public void setNodeCount(int nodeCount) {
		this.nodeCount = nodeCount;
	}

	public long getParentNodeCount() {
		return parentNodeCount;
	}

	public void setParentNodeCount(long parentNodeCount) {
		this.parentNodeCount = parentNodeCount;
	}

	public long getParentEdgeCount() {
		return parentEdgeCount;
	}

	public void setParentEdgeCount(long parentEdgeCount) {
		this.parentEdgeCount = parentEdgeCount;
	}
}
