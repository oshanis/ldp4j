/**
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   This file is part of the LDP4j Project:
 *     http://www.ldp4j.org/
 *
 *   Center for Open Middleware
 *     http://www.centeropenmiddleware.com/
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   Copyright (C) 2014 Center for Open Middleware.
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *             http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 *   Artifact    : org.ldp4j.framework:ldp4j-application-examples:1.0.0-SNAPSHOT
 *   Bundle      : ldp4j-application-examples-1.0.0-SNAPSHOT.jar
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 */
package org.ldp4j.example;

import java.net.URI;

import org.ldp4j.application.data.DataSet;
import org.ldp4j.application.data.ManagedIndividual;
import org.ldp4j.application.data.ManagedIndividualId;
import org.ldp4j.application.data.Property;
import org.ldp4j.application.data.validation.ValidationConstraint;
import org.ldp4j.application.data.validation.ValidationConstraintFactory;
import org.ldp4j.application.data.validation.ValidationReport;
import org.ldp4j.application.data.validation.Validator;
import org.ldp4j.application.ext.Deletable;
import org.ldp4j.application.ext.ContentProcessingException;
import org.ldp4j.application.ext.InconsistentContentException;
import org.ldp4j.application.ext.Modifiable;
import org.ldp4j.application.ext.annotations.Attachment;
import org.ldp4j.application.ext.annotations.Resource;
import org.ldp4j.application.session.ResourceSnapshot;
import org.ldp4j.application.session.WriteSession;
import org.ldp4j.application.session.WriteSessionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Resource(
	id=PersonHandler.ID,
	attachments={
		@Attachment(
			id="address",
			path="address",
			predicate="http://www.ldp4j.org/vocabularies/example#address",
			handler=AddressHandler.class),
		@Attachment(
			id="books",
			path="books",
			handler=BookContainerHandler.class),	
		@Attachment(
			id=PersonHandler.RELATIVES_ID,
			path=PersonHandler.RELATIVES_PATH,
			handler=RelativeContainerHandler.class)	
	}
)
public class PersonHandler extends InMemoryResourceHandler implements Modifiable, Deletable {

	private static final Logger LOGGER=LoggerFactory.getLogger(PersonHandler.class);
	
	public static final String ID="personTemplate";
	
	public static final String RELATIVES_ID   = "personRelatives";
	public static final String RELATIVES_PATH = "relatives";

	public static final URI READ_ONLY_PROPERTY = URI.create("http://www.example.org/vocab#creationDate");
	
	public PersonHandler() {
		super("Person");
	}

	@Override
	public void delete(ResourceSnapshot resource, WriteSession session) {
		DataSet dataSet = get(resource);
		try {
			logDebug(resource,"Deleting state:%n%s",dataSet);
			remove(resource.name());
			session.delete(resource);
			session.saveChanges();
		} catch (WriteSessionException e) {
			// Recover if failed
			add(resource.name(),dataSet);
			throw new IllegalStateException("Deletion failed",e);
		}
	}

	@Override
	public void update(ResourceSnapshot resource, DataSet content, WriteSession session) throws ContentProcessingException {
		DataSet dataSet = get(resource);
		logDebug(resource, "Enforcing consistency...");
		enforceConsistency(resource,content,dataSet);
		try {
			logDebug(resource,"Persisting new state:%n%s",content);
			add(resource.name(),content);
			session.modify(resource);
			session.saveChanges();
		} catch (WriteSessionException e) {
			// Recover if failed
			add(resource.name(),dataSet);
			logError(resource,e,"Something went wrong",e);
			throw new IllegalStateException("Update failed",e);
		}
	}

	protected void logDebug(ResourceSnapshot resource, String message, Object... args) {
		if(LOGGER.isDebugEnabled()) {
			LOGGER.debug("["+resource.name()+"] "+String.format(message,args));
		}
	}

	protected void logError(ResourceSnapshot resource, Throwable t, String message, Object... args) {
		if(LOGGER.isErrorEnabled()) {
			LOGGER.error("["+resource.name()+"] "+String.format(message,args),t);
		}
	}

	protected void enforceConsistency(ResourceSnapshot resource, DataSet content, DataSet dataSet) throws ContentProcessingException {
		ManagedIndividualId id = ManagedIndividualId.createId(resource.name(),PersonHandler.ID);
		ManagedIndividual stateIndividual = 
			dataSet.
				individual(
					id, 
					ManagedIndividual.class);
		Property stateProperty=
				stateIndividual.property(PersonHandler.READ_ONLY_PROPERTY);

		ValidationConstraint<Property> constraint=null;
		if(stateProperty!=null) {
			constraint=ValidationConstraintFactory.readOnlyProperty(stateProperty);
		} else {
			constraint=ValidationConstraintFactory.readOnlyProperty(id,PersonHandler.READ_ONLY_PROPERTY);
		}
		Validator helper = 
				Validator.
					builder().
						withPropertyConstraint(constraint).
						build();
		
		ValidationReport report = helper.validate(content);
		if(!report.isValid()) {
			ContentProcessingException error = new InconsistentContentException("Validation failed: "+report.validationFailures());
			logError(resource,error,"Something went wrong when validating %n%s",content);
			throw error;
		}
	}

}