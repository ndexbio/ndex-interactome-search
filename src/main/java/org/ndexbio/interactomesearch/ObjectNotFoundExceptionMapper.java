package org.ndexbio.interactomesearch;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.ext.ExceptionMapper;

import org.ndexbio.model.exceptions.ObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class ObjectNotFoundExceptionMapper implements ExceptionMapper<ObjectNotFoundException> {
	static Logger logger = LoggerFactory.getLogger(ObjectNotFoundExceptionMapper.class);

    @Override
    public Response toResponse(ObjectNotFoundException exception)
    {
    	MDC.put("error", exception.getMessage());
    	logger.error("SERVER ERROR:", exception.getMessage());
        return Response
            .status(Status.NOT_FOUND)
            .entity(exception.getNdexExceptionInJason())
            .type("application/json")
            .build();
    }
}
