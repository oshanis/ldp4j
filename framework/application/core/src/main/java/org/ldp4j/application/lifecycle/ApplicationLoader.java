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
package org.ldp4j.application.lifecycle;

import org.ldp4j.application.endpoint.EndpointFactoryService;
import org.ldp4j.application.engine.ApplicationBootstrapException;
import org.ldp4j.application.engine.ApplicationConfigurationException;
import org.ldp4j.application.ext.Application;
import org.ldp4j.application.ext.Configuration;
import org.ldp4j.application.resource.ResourceFactoryService;
import org.ldp4j.application.session.WriteSession;
import org.ldp4j.application.session.WriteSessionConfiguration;
import org.ldp4j.application.session.WriteSessionService;
import org.ldp4j.application.spi.EndpointRepository;
import org.ldp4j.application.spi.RepositoryRegistry;
import org.ldp4j.application.spi.ResourceRepository;
import org.ldp4j.application.spi.RuntimeInstance;
import org.ldp4j.application.spi.ServiceRegistry;
import org.ldp4j.application.template.TemplateManagementService;


final class ApplicationLoader<T extends Configuration> {

	private final Class<? extends Application<T>> appClass;
	private WriteSessionService writeSessionService;
	private TemplateManagementService templateManagementService;
	private EndpointFactoryService endpointFactoryService;
	private ResourceFactoryService resourceFactoryService;
	private EndpointRepository endpointRepository;
	private ResourceRepository resourceRepository;

	private ApplicationLoader(Class<? extends Application<T>> appClass) {
		this.appClass = appClass;

		RepositoryRegistry repositoryRegistry = RuntimeInstance.getInstance().getRepositoryRegistry();
		this.endpointRepository=repositoryRegistry.getEndpointRepository();
		this.resourceRepository=repositoryRegistry.getResourceRepository();

		ServiceRegistry serviceRegistry = RuntimeInstance.getInstance().getServiceRegistry();
		this.writeSessionService=serviceRegistry.getService(WriteSessionService.class);
		this.templateManagementService=serviceRegistry.getService(TemplateManagementService.class);
		this.endpointFactoryService=serviceRegistry.getService(EndpointFactoryService.class);
		this.resourceFactoryService=serviceRegistry.getService(ResourceFactoryService.class);
	}
	
	private WriteSessionService writeSessionService() {
		return this.writeSessionService;
	}

	private EndpointFactoryService endpointFactoryService() {
		return this.endpointFactoryService;
	}

	private ResourceFactoryService resourceFactoryService() {
		return this.resourceFactoryService;
	}

	private EndpointRepository endpointRepository() {
		return this.endpointRepository;
	}

	private ResourceRepository resourceRepository() {
		return this.resourceRepository;
	}

	private TemplateManagementService templateManagementService() {
		return this.templateManagementService;
	}

	ApplicationLoader<T> withEndpointRepository(EndpointRepository endpointRepository) {
		if(endpointRepository!=null) {
			this.endpointRepository = endpointRepository;
		}
		return this;
	}

	ApplicationLoader<T> withResourceRepository(ResourceRepository resourceRepository) {
		if(resourceRepository!=null) {
			this.resourceRepository = resourceRepository;
		}
		return this;
	}

	ApplicationLoader<T> withWriteSessionService(WriteSessionService writeSessionService) {
		if(writeSessionService!=null) {
			this.writeSessionService = writeSessionService;
		}
		return this;
	}

	ApplicationLoader<T> withEndpointFactoryService(EndpointFactoryService endpointFactoryService) {
		if(endpointFactoryService!=null) {
			this.endpointFactoryService = endpointFactoryService;
		}
		return this;
	}

	ApplicationLoader<T> withResourceFactoryService(ResourceFactoryService resourceFactoryService) {
		if(resourceFactoryService!=null) {
			this.resourceFactoryService = resourceFactoryService;
		}
		return this;
	}

	ApplicationLoader<T> withTemplateManagementService(TemplateManagementService templateManagementService) {
		if(templateManagementService!=null) {
			this.templateManagementService = templateManagementService;
		}
		return this;
	}

	Application<T> bootstrap() throws ApplicationBootstrapException {
		Application<T> application=instantiateApplication();
		T configuration = instantiateConfiguration(application);
		setup(application, configuration);
		initialize(application);
		return application;
	}

	private void initialize(Application<T> application) throws ApplicationConfigurationException {
		WriteSession session = writeSessionService().createSession(WriteSessionConfiguration.builder().build());
		application.initialize(session);
		writeSessionService().terminateSession(session);
	}

	private void setup(Application<T> application, T configuration) throws ApplicationConfigurationException {
		BootstrapImpl<T> bootstrap=new BootstrapImpl<T>(configuration,templateManagementService());
		EnvironmentImpl environment=
			new EnvironmentImpl(
				templateManagementService(), 
				resourceFactoryService(), 
				endpointFactoryService(), 
				resourceRepository(), 
				endpointRepository()
			);
		application.setup(environment,bootstrap);
		bootstrap.configureTemplates();
		environment.configureRootResources();
	}
	
	private Application<T> instantiateApplication() throws ApplicationBootstrapException {
		try {
			return this.appClass.newInstance();
		} catch (InstantiationException e) {
			throw new ApplicationBootstrapException(e);
		} catch (IllegalAccessException e) {
			throw new ApplicationBootstrapException(e);
		}
	}

	private T instantiateConfiguration(Application<T> app) throws ApplicationBootstrapException {
		try {
			Class<T> configurationClass = app.getConfigurationClass();
			return configurationClass.newInstance();
		} catch (InstantiationException e) {
			throw new ApplicationBootstrapException("Could not load configuration", e);
		} catch (IllegalAccessException e) {
			throw new ApplicationBootstrapException("Could not load configuration", e);
		}
	}
	
	static <T extends Configuration> ApplicationLoader<T> newInstance(Class<? extends Application<T>> appClass) throws ApplicationBootstrapException {
		return new ApplicationLoader<T>(appClass);
	}

}