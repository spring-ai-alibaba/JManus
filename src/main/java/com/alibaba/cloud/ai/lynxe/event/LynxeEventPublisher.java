/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.lynxe.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LynxeEventPublisher {

	private static final Logger logger = LoggerFactory.getLogger(LynxeEventPublisher.class);

	// Listeners cannot be dynamically registered, no need for thread safety
	private Map<Class<? extends LynxeEvent>, List<LynxeListener<? super LynxeEvent>>> listeners = new HashMap<>();

	public void publish(LynxeEvent event) {
		Class<? extends LynxeEvent> eventClass = event.getClass();
		for (Map.Entry<Class<? extends LynxeEvent>, List<LynxeListener<? super LynxeEvent>>> entry : listeners
			.entrySet()) {
			// Parent classes can also be notified here
			if (entry.getKey().isAssignableFrom(eventClass)) {
				for (LynxeListener<? super LynxeEvent> listener : entry.getValue()) {
					try {
						listener.onEvent(event);
					}
					catch (Exception e) {
						logger.error("Error occurred while processing event: {}", e.getMessage(), e);
					}
				}
			}
		}
	}

	void registerListener(Class<? extends LynxeEvent> eventClass, LynxeListener<? super LynxeEvent> listener) {
		List<LynxeListener<? super LynxeEvent>> lynxeListeners = listeners.get(eventClass);
		if (lynxeListeners == null) {
			List<LynxeListener<? super LynxeEvent>> list = new ArrayList<>();
			list.add(listener);
			listeners.put(eventClass, list);
		}
		else {
			lynxeListeners.add(listener);
		}
	}

}

