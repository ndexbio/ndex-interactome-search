package org.ndexbio.interactomesearch.object;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)

public class SearchStatus {
	
	public static final String submitted = "submitted";
	public static final String processing = "processing";
	public static final String complete = "complete";
	public static final String failed = "failed";

	private String status;
    private String message;
    private int progress;
	private long wallTime;
	private int numberOfHits;
	private int start;
	private int size;
	private Set<String> query;
	
	private Hashtable<String, Map<String, Object>> sources;
    
	public SearchStatus() {
		setQuery(new TreeSet<>());
		setSources(new Hashtable<>(60));
		progress  = 0;
		wallTime = 0;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public int getProgress() {
		return progress;
	}

	public void setProgress(int progress) {
		this.progress = progress;
	}

	public long getWallTime() {
		return wallTime;
	}

	public void setWallTime(long wallTime) {
		this.wallTime = wallTime;
	}

	public int getNumberOfHits() {
		return numberOfHits;
	}

	public void setNumberOfHits(int numberOfHits) {
		this.numberOfHits = numberOfHits;
	}

	public int getStart() {
		return start;
	}

	public void setStart(int start) {
		this.start = start;
	}

	public int getSize() {
		return size;
	}

	public void setSize(int size) {
		this.size = size;
	}

	public Set<String> getQuery() {
		return query;
	}

	public void setQuery(Set<String> query) {
		this.query = query;
	}

	public Hashtable<String,Map<String, Object>> getSources() {
		return sources;
	}

	public void setSources (Hashtable<String,Map<String, Object>> sources) {
		this.sources = sources;
	}
	
	
 }
