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
 *   Artifact    : org.ldp4j.framework:ldp4j-application-core:1.0.0-SNAPSHOT
 *   Bundle      : ldp4j-application-core-1.0.0-SNAPSHOT.jar
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 */
package org.ldp4j.application;

import java.util.Collections;
import java.util.Date;
import java.util.Map;

import org.ldp4j.application.data.DataSet;
import org.ldp4j.application.data.ManagedIndividualId;
import org.ldp4j.application.endpoint.Endpoint;
import org.ldp4j.application.endpoint.EndpointLifecycleListener;
import org.ldp4j.application.endpoint.EndpointManagementService;
import org.ldp4j.application.engine.ApplicationInitializationException;
import org.ldp4j.application.engine.context.ApplicationContext;
import org.ldp4j.application.engine.context.ApplicationContextException;
import org.ldp4j.application.engine.context.ApplicationExecutionException;
import org.ldp4j.application.engine.context.Capabilities;
import org.ldp4j.application.engine.context.ContentPreferences;
import org.ldp4j.application.engine.context.EntityTag;
import org.ldp4j.application.engine.context.PublicResource;
import org.ldp4j.application.engine.context.PublicResourceVisitor;
import org.ldp4j.application.engine.lifecycle.ApplicationLifecycleListener;
import org.ldp4j.application.ext.Application;
import org.ldp4j.application.ext.Configuration;
import org.ldp4j.application.ext.Deletable;
import org.ldp4j.application.ext.Modifiable;
import org.ldp4j.application.ext.ResourceHandler;
import org.ldp4j.application.lifecycle.ApplicationLifecycleService;
import org.ldp4j.application.lifecycle.LifecycleException;
import org.ldp4j.application.lifecycle.LifecycleManager;
import org.ldp4j.application.resource.Container;
import org.ldp4j.application.resource.Resource;
import org.ldp4j.application.resource.ResourceControllerService;
import org.ldp4j.application.resource.ResourceId;
import org.ldp4j.application.session.WriteSessionConfiguration;
import org.ldp4j.application.session.WriteSessionService;
import org.ldp4j.application.spi.EndpointRepository;
import org.ldp4j.application.spi.RepositoryRegistry;
import org.ldp4j.application.spi.ResourceRepository;
import org.ldp4j.application.spi.RuntimeInstance;
import org.ldp4j.application.spi.ServiceRegistry;
import org.ldp4j.application.template.ResourceTemplate;
import org.ldp4j.application.template.TemplateIntrospector;
import org.ldp4j.application.template.TemplateManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Maps;

public final class DefaultApplicationContext implements ApplicationContext {

	private static final class GonePublicResource implements PublicResource {

		private final Endpoint endpoint;

		private GonePublicResource(Endpoint endpoint) {
			this.endpoint = endpoint;
		}

		@Override
		public Status status() {
			return Status.GONE;
		}

		@Override
		public String path() {
			return endpoint.path();
		}

		@Override
		public EntityTag entityTag() {
			return endpoint.entityTag();
		}

		@Override
		public Date lastModified() {
			return endpoint.lastModified();
		}

		@Override
		public Capabilities capabilities() {
			return new MutableCapabilities();
		}

		@Override
		public Map<String, PublicResource> attachments() {
			return Collections.emptyMap();
		}

		@Override
		public ManagedIndividualId individualId() {
			return ManagedIndividualId.createId(endpoint.resourceId().name(), endpoint.resourceId().templateId());
		}

		@Override
		public <T> T accept(PublicResourceVisitor<T> visitor) {
			throw new UnsupportedOperationException("The endpoint is gone");
		}

		@Override
		public DataSet entity(ContentPreferences contentPreferences) throws ApplicationExecutionException {
			throw new UnsupportedOperationException("The endpoint is gone");
		}

		@Override
		public void delete() throws ApplicationExecutionException {
			throw new UnsupportedOperationException("The endpoint is gone");
		}

		@Override
		public void modify(DataSet dataSet) throws ApplicationExecutionException {
			throw new UnsupportedOperationException("The endpoint is gone");
		}
	}

	private final class LocalEndpointLifecycleListener implements EndpointLifecycleListener {
		@Override
		public void endpointCreated(Endpoint endpoint) {
		}
		@Override
		public void endpointDeleted(Endpoint endpoint) {
			DefaultApplicationContext.this.goneEndpoints.put(endpoint.path(),endpoint);
		}
	}

	private static Logger LOGGER=LoggerFactory.getLogger(DefaultApplicationContext.class);

	private static DefaultApplicationContext context;

	private ResourceRepository resourceRepository;
	private EndpointRepository endpointRepository;
	private ApplicationLifecycleService applicationLifecycleService;
	private TemplateManagementService templateManagementService;
	private EndpointManagementService endpointManagementService;
	private WriteSessionService writeSessionService;

	private Application<Configuration> application;

	private ResourceControllerService resourceControllerService;

	private final DefaultPublicResourceFactory factory;
	private final EndpointLifecycleListener endpointLifecycleListener;
	private final Map<String,Endpoint> goneEndpoints;


	private DefaultApplicationContext() {
		this.factory=DefaultPublicResourceFactory.newInstance(this);
		this.goneEndpoints=Maps.newLinkedHashMap();
		this.endpointLifecycleListener = new LocalEndpointLifecycleListener();
	}

	private static <T> T checkNotNull(T object, String message) {
		if(object==null) {
			throw new ApplicationContextException(message);
		}
		return object;
	}

	private DefaultApplicationContext withWriteSessionService(WriteSessionService service) {
		this.writeSessionService = checkNotNull(service,"Write session service cannot be null");
		return this;
	}

	private DefaultApplicationContext withEndpointManagementService(EndpointManagementService service) {
		this.endpointManagementService = checkNotNull(service,"Endpoint management service cannot be null");
		return this;
	}

	private DefaultApplicationContext withApplicationLifecycleService(ApplicationLifecycleService service) {
		this.applicationLifecycleService = checkNotNull(service,"Application lifecycle service cannot be null");
		return this;
	}

	private DefaultApplicationContext withTemplateManagementService(TemplateManagementService service) {
		this.templateManagementService = checkNotNull(service,"Template management service cannot be null");
		return this;
	}

	private DefaultApplicationContext withResourceControllerService(ResourceControllerService service) {
		this.resourceControllerService = checkNotNull(service,"Resource controller service cannot be null");
		return this;
	}

	private DefaultApplicationContext withResourceRepository(ResourceRepository resourceRepository) {
		this.resourceRepository=checkNotNull(resourceRepository,"Resource repository cannot be null");
		return this;
	}

	private DefaultApplicationContext withEndpointRepository(EndpointRepository endpointRepository) {
		this.endpointRepository=checkNotNull(endpointRepository,"Endpoint repository cannot be null");
		return this;
	}

	private String applicationFailureMessage(String message, Object... objects) {
		return "[" + this.application.getName() + "] " + String.format(message,objects);
	}

	private Application<Configuration> application() {
		return application;
	}

	private void initializeComponents() throws LifecycleException {
		LifecycleManager.init(this.resourceRepository);
		LifecycleManager.init(this.endpointRepository);
		LifecycleManager.init(this.endpointManagementService);
		LifecycleManager.init(this.resourceControllerService);
		LifecycleManager.init(this.templateManagementService);
		LifecycleManager.init(this.writeSessionService);
	}

	private void shutdownComponents() {
		shutdown(this.endpointManagementService);
		shutdown(this.resourceControllerService);
		shutdown(this.templateManagementService);
		shutdown(this.writeSessionService);
		shutdown(this.endpointRepository);
		shutdown(this.resourceRepository);
	}

	private <T> void shutdown(T object) {
		try {
			LifecycleManager.shutdown(object);
		} catch (LifecycleException e) {
			LOGGER.error("Could not shutdown "+object,e);
		}
	}

	DataSet getResource(Endpoint endpoint) throws ApplicationExecutionException {
		ResourceId resourceId=endpoint.resourceId();
		Resource resource = this.resourceRepository.find(resourceId,Resource.class);
		if(resource==null) {
			String errorMessage = applicationFailureMessage("Could not find resource for endpoint '%s'",endpoint);
			LOGGER.error(errorMessage);
			throw new ApplicationExecutionException(errorMessage);
		}
		try {
			return this.resourceControllerService.getResource(resource);
		} catch (Exception e) {
			String errorMessage = applicationFailureMessage("Resource '%s' retrieval failed ",endpoint);
			LOGGER.error(errorMessage,e);
			throw new ApplicationExecutionException(errorMessage,e);
		}
	}

	Resource resolveResource(Endpoint endpoint) {
		return this.resourceRepository.find(endpoint.resourceId(), Resource.class);
	}

	Endpoint resolveResource(ResourceId id) {
		return this.endpointRepository.endpointOfResource(id);
	}

	Resource createResource(Endpoint endpoint, DataSet dataSet, String desiredPath) throws ApplicationExecutionException {
		ResourceId resourceId=endpoint.resourceId();
		Container resource = this.resourceRepository.find(resourceId,Container.class);
		if(resource==null) {
			String errorMessage = applicationFailureMessage("Could not find container for endpoint '%s'",endpoint);
			LOGGER.error(errorMessage);
			throw new ApplicationExecutionException(errorMessage);
		}
		try {
			return this.resourceControllerService.createResource(resource, dataSet,desiredPath);
		} catch (Exception e) {
			String errorMessage = applicationFailureMessage("Resource create failed at '%s'",endpoint);
			LOGGER.error(errorMessage,e);
			throw new ApplicationExecutionException(errorMessage,e);
		}
	}

	void deleteResource(Endpoint endpoint) throws ApplicationExecutionException {
		ResourceId resourceId=endpoint.resourceId();
		Resource resource = this.resourceRepository.find(resourceId,Resource.class);
		if(resource==null) {
			String errorMessage = applicationFailureMessage("Could not find container for endpoint '%s'",endpoint);
			LOGGER.error(errorMessage);
			throw new ApplicationExecutionException(errorMessage);
		}
		try {
			this.resourceControllerService.deleteResource(resource, WriteSessionConfiguration.builder().build());
		} catch (Exception e) {
			String errorMessage = applicationFailureMessage("Resource deletion failed at '%s'",endpoint);
			LOGGER.error(errorMessage,e);
			throw new ApplicationExecutionException(errorMessage,e);
		}
	}

	void modifyResource(Endpoint endpoint, DataSet dataSet) throws ApplicationExecutionException {
		ResourceId resourceId=endpoint.resourceId();
		Resource resource = this.resourceRepository.find(resourceId,Resource.class);
		if(resource==null) {
			String errorMessage = applicationFailureMessage("Could not find resource for endpoint '%s'",endpoint);
			LOGGER.error(errorMessage);
			throw new ApplicationExecutionException(errorMessage);
		}
		try {
			this.resourceControllerService.updateResource(resource,dataSet, WriteSessionConfiguration.builder().build());
		} catch (Exception e) {
			String errorMessage = applicationFailureMessage("Resource modification failed at '%s'",endpoint);
			LOGGER.error(errorMessage,e);
			throw new ApplicationExecutionException(errorMessage,e);
		}
	}

	Capabilities endpointCapabilities(Endpoint endpoint) {
		MutableCapabilities result=new MutableCapabilities();
		Resource resource = resolveResource(endpoint);
		ResourceTemplate template=resourceTemplate(resource);
		Class<? extends ResourceHandler> handlerClass = template.handlerClass();
		result.setModifiable(Modifiable.class.isAssignableFrom(handlerClass));
		result.setDeletable(Deletable.class.isAssignableFrom(handlerClass) && !resource.isRoot());
		// TODO: Analyze how to provide patch support
		result.setPatchable(false);
		TemplateIntrospector introspector = TemplateIntrospector.newInstance(template);
		result.setFactory(introspector.isContainer());
		return result;
	}

	ResourceTemplate resourceTemplate(Resource resource) {
		return this.templateManagementService.findTemplateById(resource.id().templateId());
	}

	public void initialize(String applicationClassName) throws ApplicationInitializationException {
		try {
			this.endpointManagementService.registerEndpointLifecycleListener(this.endpointLifecycleListener);
			this.application = this.applicationLifecycleService.initialize(applicationClassName);
		} catch (ApplicationInitializationException e) {
			String errorMessage = "Application '"+applicationClassName+"' initilization failed";
			LOGGER.error(errorMessage,e);
			shutdownComponents();
			throw e;
		}
	}

	public boolean shutdown() {
		this.applicationLifecycleService.shutdown();
		this.endpointManagementService.deregisterEndpointLifecycleListener(this.endpointLifecycleListener);
		shutdownComponents();
		return this.applicationLifecycleService.isShutdown();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String applicationName() {
		return application().getName();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String applicationClassName() {
		return this.application.getClass().getName();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public PublicResource findResource(final String path) {
		checkNotNull(path,"Endpoint path cannot be null");
		PublicResource resolved = resolveResource(path);
		if(resolved==null) {
			Endpoint endpoint=this.goneEndpoints.get(path);
			if(endpoint!=null) {
				resolved=new GonePublicResource(endpoint);
			}
		}
		return resolved;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public PublicResource resolveResource(final String path) {
		checkNotNull(path,"Endpoint path cannot be null");
		PublicResource resolved=null;
		Endpoint endpoint = this.endpointManagementService.resolveEndpoint(path);
		if(endpoint!=null) {
			resolved = this.factory.createResource(endpoint);
		}
		return resolved;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public PublicResource resolveResource(ManagedIndividualId id) {
		checkNotNull(id,"Individual identifier cannot be null");
		return this.factory.createResource(ResourceId.createId(id.name(), id.managerId()));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void registerApplicationLifecycleListener(ApplicationLifecycleListener listener) {
		checkNotNull(listener,"Application lifecycle listener cannot be null");
		this.applicationLifecycleService.registerApplicationLifecycleListener(listener);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deregisterApplicationLifecycleListener(ApplicationLifecycleListener listener) {
		checkNotNull(listener,"Application lifecycle listener cannot be null");
		this.applicationLifecycleService.deregisterApplicationLifecycleListener(listener);
	}

	private static void setCurrentContext(DefaultApplicationContext context) {
		DefaultApplicationContext.context = context;
	}

	private static DefaultApplicationContext newApplicationContext() {
		RuntimeInstance instance = RuntimeInstance.getInstance();
		RepositoryRegistry repositoryRegistry = instance.getRepositoryRegistry();
		ServiceRegistry serviceRegistry = instance.getServiceRegistry();
		DefaultApplicationContext context=
			new DefaultApplicationContext().
				withEndpointRepository(repositoryRegistry.getEndpointRepository()).
				withResourceRepository(repositoryRegistry.getResourceRepository()).
				withApplicationLifecycleService(serviceRegistry.getService(ApplicationLifecycleService.class)).
				withTemplateManagementService(serviceRegistry.getService(TemplateManagementService.class)).
				withEndpointManagementService(serviceRegistry.getService(EndpointManagementService.class)).
				withWriteSessionService(serviceRegistry.getService(WriteSessionService.class)).
				withResourceControllerService(serviceRegistry.getService(ResourceControllerService.class));
		return context;
	}

	public static synchronized DefaultApplicationContext currentContext() {
		if(DefaultApplicationContext.context==null) {
			DefaultApplicationContext.context=newApplicationContext();
		}
		return DefaultApplicationContext.context;
	}

	public static synchronized ApplicationContext createContext(String applicationClassName) {
		// Candidate application context configuration
		DefaultApplicationContext context = newApplicationContext();

		// Candidate application context component initialization
		try {
			context.initializeComponents();
		} catch (LifecycleException e) {
			String errorMessage = "Could not initialize application context components";
			LOGGER.error(errorMessage,e);
			throw new ApplicationContextException(errorMessage,e);
		}

		// Candidate application context target application initialization
		try {
			context.initialize(applicationClassName);
		} catch (ApplicationInitializationException e) {
			String errorMessage = "Application '"+applicationClassName+"' initilization failed";
			LOGGER.error(errorMessage,e);
			context.shutdownComponents();
			throw new ApplicationContextException(errorMessage,e);
		}

		// Current application context setup
		setCurrentContext(context);
		return currentContext();
	}




}
