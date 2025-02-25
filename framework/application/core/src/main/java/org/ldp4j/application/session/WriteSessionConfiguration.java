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
package org.ldp4j.application.session;

import com.google.common.base.Objects;

public final class WriteSessionConfiguration {
	
	private String path;
	private ResourceSnapshot targetSnapshot;

	private WriteSessionConfiguration() {
	}

	private void setPath(String path) {
		this.path=path;
	}
	
	public void setTargetSnapshot(ResourceSnapshot targetSnapshot) {
		this.targetSnapshot = targetSnapshot;
	}

	public String getPath() {
		return this.path;
	}
	
	public ResourceSnapshot getTargetSnapshot() {
		return this.targetSnapshot;
	}

	@Override
	public String toString() {
		return 
			Objects.
				toStringHelper("WriteSessionConfiguration").
					add("path",this.path).
					add("targetSnapshot",this.targetSnapshot).
					toString();
	}

	public static WriteSessionConfiguration.WriteSessionConfigurationBuilder builder() {
		return new WriteSessionConfigurationBuilder();
	}
	
	public static final class WriteSessionConfigurationBuilder {
		
		private final WriteSessionConfiguration configuration;

		private WriteSessionConfigurationBuilder() {
			this.configuration = new WriteSessionConfiguration();
		}
		
		public WriteSessionConfigurationBuilder withPath(String path) {
			this.configuration.setPath(path);
			return this;
		}
		
		public WriteSessionConfiguration build() {
			return this.configuration;
		}

	}

}