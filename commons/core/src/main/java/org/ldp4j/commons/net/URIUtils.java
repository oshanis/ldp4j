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
 *   Artifact    : org.ldp4j.commons:ldp4j-commons-core:1.0.0-SNAPSHOT
 *   Bundle      : ldp4j-commons-core-1.0.0-SNAPSHOT.jar
 * #-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=-=#
 */
package org.ldp4j.commons.net;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import org.ldp4j.commons.Assertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class URIUtils {
	
	private static final Logger LOGGER=LoggerFactory.getLogger(URIUtils.class);

	private URIUtils() {
	}
	
	private static URL createFallback(URI uri) {
		URL fallback=null;
		String scheme = uri.getScheme();
		if(ProtocolHandlerConfigurator.isSupported(scheme)) {
			URLStreamHandler handler = ProtocolHandlerConfigurator.getHandler(scheme);
			try {
				fallback=new URL(scheme,null,0,uri.toString().replace(scheme+":",""),handler);
			} catch (MalformedURLException fatal) {
				if(LOGGER.isWarnEnabled()) {
					LOGGER.warn(String.format("Fallback solution for supported custom '%s' protocol failed. Full stack trace follows.",scheme),fatal);
				}
			}
		}
		if(LOGGER.isDebugEnabled()) {
			LOGGER.debug(
				String.format(
					fallback==null?
						"Support for custom '%s' protocol not configured":
						"Support for custom '%s' protocol was configured, but the handler was not instantiated automatically. Fallback solution provided.",
					scheme));
		}
		return fallback;
	}

	private static URL tryFallback(URI uri, MalformedURLException e) throws MalformedURLException {
		URL fallback = null;
		if(e.getMessage().startsWith("unknown protocol")) {
			fallback=createFallback(uri);
		}
		if(fallback!=null) {
			return fallback;
		} else {
			throw e;
		}
	}

	public static URL toURL(URI uri) throws MalformedURLException {
		Assertions.notNull(uri, "uri");
		try {
			return uri.toURL();
		} catch(MalformedURLException e) {
			return tryFallback(uri, e);
		}
	}

	public static URI resolve(URI base, URI target) {
		if(
			!base.getScheme().equals(target.getScheme()) || 
			!base.getAuthority().equals(target.getAuthority())) {
			return target;
		}
		URI result = walkthrough(base.normalize(),target.normalize());
		System.out.printf(", Resolution: %s%n",base,target,result);
		System.out.flush();
		return result;
	}

	private static String[] tokenize(String path) {
		StringTokenizer tokenizer=new StringTokenizer(path,"/");
		List<String> segments=new ArrayList<String>();
		while(tokenizer.hasMoreTokens()) {
			segments.add(tokenizer.nextToken());
		}
		if(path.endsWith("/")) {
			segments.add("");
		}
		return segments.toArray(new String[segments.size()]);
	}
	
	
	private static URI walkthrough(URI base, URI target) {
//		if(base.equals(target)){
//			return URI.create("");
//		} else if(base.getPath().endsWith("/") && target.toString().startsWith(base.toString())) {
//			return URI.create(target.toString().substring(base.toString().length()));
//		}
//		String[] basePath=tokenize(base.getPath());
//		String[] targetPath=tokenize(target.getPath());
		System.out.printf("Base: %s, Target: %s%n",base,target);
		String[] basePath=base.getPath().split("/");
		String[] targetPath=target.getPath().split("/");
		int common = findCommons(basePath, targetPath);
		System.out.printf("- Base path: %s%n", base.getPath());
		System.out.printf("- Base segments: %s%n", Arrays.toString(basePath));
		System.out.printf("- Target path: %s%n", base.getPath());
		System.out.printf("- Target segments: %s%n", Arrays.toString(basePath));
		System.out.printf("- # of common segments: %d%n ", common);

		if(targetPath.length==common) {
			URI result=null;
			if(base.getPath().endsWith("/") && !target.getPath().endsWith("/")) {
				result=URI.create("../"+basePath[common-1]);
			} else if(!base.getPath().endsWith("/") && target.getPath().endsWith("/")) {
				result=URI.create("./"+basePath[common-1]+"/");
			} else if(base.getPath().equals(target.getPath())) {
				result=URI.create("");
			} else {
				throw new IllegalStateException("Don't know how to shorten...");
			}
			return result;
		} else {
			List<String> segments=
					getSegments(
						targetPath, 
						common,
						basePath.length-common);

			return recreateFromSegments(segments, target);
		}
	}

	private static int findCommons(String[] basePath, String[] targetPath) {
		int common=0;
		while(common<basePath.length && common<targetPath.length) {
			if(basePath[common].equals(targetPath[common])) {
				common++;
			} else {
				break;
			}
		}
		return common;
	}

	private static List<String> getSegments(
			String[] targetPath,
			int commonSegments, 
			int discardedSegments) {
		List<String> segments=new ArrayList<String>();
		for(int j=0;j<discardedSegments;j++) {
			segments.add("..");
		}
		segments.
			addAll(
				Arrays.asList(
					Arrays.copyOfRange(
						targetPath,
						commonSegments,
						targetPath.length)));
		return segments;
	}

	private static URI recreateFromSegments(List<String> segments, URI target) {
		StringBuilder builder=new StringBuilder();
		for(Iterator<String> it=segments.iterator();it.hasNext();) {
			builder.append(it.next());
			if(it.hasNext()) {
				builder.append("/");
			}
		}

		if(target.getFragment()!=null) {
			builder.append("#").append(target.getFragment());
		}
		if(target.getQuery()!=null) {
			builder.append("?").append(target.getQuery());
		}
		
		return URI.create(builder.toString());
	}

}
