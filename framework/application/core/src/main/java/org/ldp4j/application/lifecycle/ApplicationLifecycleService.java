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

import static com.google.common.base.Preconditions.checkState;

import org.ldp4j.application.engine.ApplicationBootstrapException;
import org.ldp4j.application.engine.ApplicationInitializationException;
import org.ldp4j.application.engine.lifecycle.ApplicationLifecycleListener;
import org.ldp4j.application.engine.lifecycle.ApplicationState;
import org.ldp4j.application.engine.util.ListenerManager;
import org.ldp4j.application.engine.util.Notification;
import org.ldp4j.application.ext.Application;
import org.ldp4j.application.ext.Configuration;
import org.ldp4j.application.spi.Service;
import org.ldp4j.application.spi.ServiceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ApplicationLifecycleService implements Service {

	private static final Logger LOGGER=LoggerFactory.getLogger(ApplicationLifecycleService.class); 
	
	private final class ApplicationStateChangeNotification implements
			Notification<ApplicationLifecycleListener> {
		private final ApplicationState state;

		private ApplicationStateChangeNotification(ApplicationState state) {
			this.state = state;
		}

		@Override
		public void propagate(ApplicationLifecycleListener listener) {
			listener.applicationStateChanged(state);
		}
	}

	private static class ApplicationFrontendServiceBuilder extends ServiceBuilder<ApplicationLifecycleService> {
		
		private ApplicationFrontendServiceBuilder() {
			super(ApplicationLifecycleService.class);
		}

		public ApplicationLifecycleService build() {
			return new ApplicationLifecycleService();
		}
		
	}

	private volatile ApplicationState state;

	private Application<?> application;
	private ListenerManager<ApplicationLifecycleListener> listenerManager;
	
	private ApplicationLifecycleService() {
		this.listenerManager=ListenerManager.<ApplicationLifecycleListener>newInstance();
		this.state=ApplicationState.UNDEFINED;
	}

	@SuppressWarnings("unchecked")
	private <T extends Configuration> Class<? extends Application<T>> loadApplicationClass(String className) throws ApplicationInitializationException {
		ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
		try {
			Class<?> targetApplicationClass = contextClassLoader.loadClass(className);
			if(!Application.class.isAssignableFrom(targetApplicationClass)) {
				throw new ApplicationInitializationException("Invalid application class");
			}
			return (Class<? extends Application<T>>)targetApplicationClass.asSubclass(Application.class);
		} catch (ClassNotFoundException e) {
			throw new ApplicationInitializationException("Unknown application class", e);
		}
	}

	private void notifyApplicationStateChange(final ApplicationState state) {
		this.listenerManager.notify(new ApplicationStateChangeNotification(state));
		this.state=state;
	}

	public void registerApplicationLifecycleListener(ApplicationLifecycleListener listener) {
		this.listenerManager.registerListener(listener);
	}

	public void deregisterApplicationLifecycleListener(ApplicationLifecycleListener listener) {
		this.listenerManager.deregisterListener(listener);
	}

	public <T extends Configuration> Application<T> initialize(String className) throws ApplicationInitializationException {
		checkState(this.state.canInitialize(),"Application cannot be initialized at this moment ("+this.state+")");
		if(className==null) {
			notifyApplicationStateChange(ApplicationState.UNDEFINED);
			throw new ApplicationInitializationException("No LDP4j application class specified");
		}
		ApplicationState newState = ApplicationState.UNAVAILABLE;
		try {
			ApplicationLoader<T> helper = ApplicationLoader.newInstance(this.<T>loadApplicationClass(className));
			Application<T> application = helper.bootstrap();
			newState=ApplicationState.AVAILABLE;
			this.application = application;
			LOGGER.info("Application '{}' ({}) initialized.",this.application.getName(),this.application.getClass().getCanonicalName());
			return application;
		} catch (ApplicationBootstrapException e) {
			throw e;
		} finally {
			notifyApplicationStateChange(newState);
		}
	}
	
	public void shutdown() {
		if(!this.state.isShutdown()) {
			if(this.application!=null) {
				this.application.shutdown();
				LOGGER.info("Application '{}' ({}) shutdown.",this.application.getName(),this.application.getClass().getCanonicalName());
			}
			notifyApplicationStateChange(ApplicationState.SHUTDOWN);
		}
	}	

	public static ServiceBuilder<ApplicationLifecycleService> serviceBuilder() {
		return new ApplicationFrontendServiceBuilder();
	}
	
	public static ApplicationLifecycleService defaultService() {
		return serviceBuilder().build();
	}

	public String getApplicationName() {
		return this.application!=null?this.application.getName():"<unknown>";
	}

	public boolean isShutdown() {
		return this.state.isShutdown() && this.application!=null;
	}

}