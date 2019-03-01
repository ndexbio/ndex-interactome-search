package org.ndexbio;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

public class NeighborhoodQueryApplication extends Application {

    	 private final Set<Object> _providers = new HashSet<>();

	  public NeighborhoodQueryApplication() {
		  _providers.add(new MessageResource());
		 
	        _providers.add(new DefaultExceptionMapper());
	        _providers.add(new BadRequestExceptionMapper());

	  }

	  @Override
	  public Set<Object> getSingletons() {
	 //   HashSet<Object> set = new HashSet<>();
	 //   set.add(new MessageResource());
	    return _providers;
	  }
}