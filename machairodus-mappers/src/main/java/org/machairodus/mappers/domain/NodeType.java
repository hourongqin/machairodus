/**
 * Copyright 2015 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 			http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.machairodus.mappers.domain;

public enum NodeType {
	BALANCER(1), SCHEDULER(2), SERVICE_NODE(3);
	
	private int value;
	private NodeType(int value) {
		this.value = value;
	}
	
	public int value() {
		return value;
	}
	
	public static NodeType value(Integer value) {
		if(value == null)
			return null;
		
		switch(value) {
			case 1:
				return BALANCER;
			case 2: 
				return SCHEDULER;
			case 3:
				return SERVICE_NODE;
			default: 
				throw new IllegalArgumentException("Unknown Node type");
		}
	}
}
