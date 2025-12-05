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
package com.alibaba.cloud.ai.lynxe.tool.crawler;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 知识星球爬虫请求参数
 */
public class ZsxqCrawlerRequestVO {

	@JsonProperty("action")
	private String action;

	@JsonProperty("group_id")
	private String groupId;

	@JsonProperty("topic_id")
	private String topicId;

	@JsonProperty("download_path")
	private String downloadPath;

	@JsonProperty("max_posts")
	private Integer maxPosts;

	public String getAction() {
		return action;
	}

	public void setAction(String action) {
		this.action = action;
	}

	public String getGroupId() {
		return groupId;
	}

	public void setGroupId(String groupId) {
		this.groupId = groupId;
	}

	public String getTopicId() {
		return topicId;
	}

	public void setTopicId(String topicId) {
		this.topicId = topicId;
	}

	public String getDownloadPath() {
		return downloadPath;
	}

	public void setDownloadPath(String downloadPath) {
		this.downloadPath = downloadPath;
	}

	public Integer getMaxPosts() {
		return maxPosts;
	}

	public void setMaxPosts(Integer maxPosts) {
		this.maxPosts = maxPosts;
	}

}
