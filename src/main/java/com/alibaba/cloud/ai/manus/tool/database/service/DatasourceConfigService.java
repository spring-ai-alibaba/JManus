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
package com.alibaba.cloud.ai.manus.tool.database.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.beans.factory.annotation.Autowired;

import com.alibaba.cloud.ai.manus.tool.database.DataSourceService;
import com.alibaba.cloud.ai.manus.tool.database.model.po.DatasourceConfigEntity;
import com.alibaba.cloud.ai.manus.tool.database.model.vo.DatasourceConfigVO;
import com.alibaba.cloud.ai.manus.tool.database.repository.DatasourceConfigRepository;

/**
 * Service for managing datasource configurations
 */
@Service
public class DatasourceConfigService {

	private static final Logger logger = LoggerFactory.getLogger(DatasourceConfigService.class);

	private final DatasourceConfigRepository repository;

	private final DataSourceService dataSourceService;

	public DatasourceConfigService(DatasourceConfigRepository repository,
			@Autowired(required = false) DataSourceService dataSourceService) {
		this.repository = repository;
		this.dataSourceService = dataSourceService;
	}

	/**
	 * Get all datasource configurations
	 */
	public List<DatasourceConfigVO> getAllConfigs() {
		logger.debug("Retrieving all datasource configurations");
		return repository.findAll().stream().map(this::mapToVO).collect(Collectors.toList());
	}

	/**
	 * Get datasource configuration by ID
	 */
	public DatasourceConfigVO getConfigById(Long id) {
		logger.debug("Retrieving datasource configuration by ID: {}", id);
		Optional<DatasourceConfigEntity> entity = repository.findById(id);
		return entity.map(this::mapToVO).orElse(null);
	}

	/**
	 * Get datasource configuration by name
	 */
	public DatasourceConfigVO getConfigByName(String name) {
		logger.debug("Retrieving datasource configuration by name: {}", name);
		Optional<DatasourceConfigEntity> entity = repository.findByName(name);
		return entity.map(this::mapToVO).orElse(null);
	}

	/**
	 * Get enabled datasource configurations
	 */
	public List<DatasourceConfigVO> getEnabledConfigs() {
		logger.debug("Retrieving enabled datasource configurations");
		return repository.findByEnable(true).stream().map(this::mapToVO).collect(Collectors.toList());
	}

	/**
	 * Create new datasource configuration
	 */
	@Transactional
	public DatasourceConfigVO createConfig(DatasourceConfigVO vo) {
		logger.info("Creating datasource configuration: {}", vo.getName());
		if (repository.existsByName(vo.getName())) {
			throw new IllegalArgumentException("Datasource configuration with name '" + vo.getName() + "' already exists");
		}

		DatasourceConfigEntity entity = mapToEntity(vo);
		entity.setCreatedAt(LocalDateTime.now());
		entity.setUpdatedAt(LocalDateTime.now());
		entity = repository.save(entity);
		logger.info("Created datasource configuration with ID: {}", entity.getId());
		return mapToVO(entity);
	}

	/**
	 * Update existing datasource configuration
	 */
	@Transactional
	public DatasourceConfigVO updateConfig(Long id, DatasourceConfigVO vo) {
		logger.info("Updating datasource configuration with ID: {}", id);
		Optional<DatasourceConfigEntity> entityOpt = repository.findById(id);
		if (entityOpt.isEmpty()) {
			throw new IllegalArgumentException("Datasource configuration with ID '" + id + "' not found");
		}

		DatasourceConfigEntity entity = entityOpt.get();
		// Check if name is being changed and if new name already exists
		if (!entity.getName().equals(vo.getName()) && repository.existsByName(vo.getName())) {
			throw new IllegalArgumentException("Datasource configuration with name '" + vo.getName() + "' already exists");
		}

		// Update fields
		entity.setName(vo.getName());
		entity.setType(vo.getType());
		entity.setEnable(vo.getEnable());
		entity.setUrl(vo.getUrl());
		entity.setDriverClassName(vo.getDriverClassName());
		entity.setUsername(vo.getUsername());
		// Only update password if a new value is provided (including empty string)
		// If password is null, keep the existing password
		if (vo.getPassword() != null) {
			entity.setPassword(vo.getPassword());
		}
		entity.setUpdatedAt(LocalDateTime.now());

		entity = repository.save(entity);
		logger.info("Updated datasource configuration with ID: {}", id);
		return mapToVO(entity);
	}

	/**
	 * Delete datasource configuration
	 */
	@Transactional
	public void deleteConfig(Long id) {
		logger.info("Deleting datasource configuration with ID: {}", id);
		if (!repository.existsById(id)) {
			throw new IllegalArgumentException("Datasource configuration with ID '" + id + "' not found");
		}
		repository.deleteById(id);
		logger.info("Deleted datasource configuration with ID: {}", id);
	}

	/**
	 * Check if datasource configuration exists by name
	 */
	public boolean existsByName(String name) {
		return repository.existsByName(name);
	}

	/**
	 * Get all enabled datasource configurations for DataSourceService initialization
	 */
	public List<DatasourceConfigEntity> getEnabledEntities() {
		return repository.findByEnable(true);
	}

	/**
	 * Map Entity to VO (password is not returned for security)
	 */
	private DatasourceConfigVO mapToVO(DatasourceConfigEntity entity) {
		DatasourceConfigVO vo = new DatasourceConfigVO();
		BeanUtils.copyProperties(entity, vo);
		// Clear password for security - never return password to frontend
		vo.setPassword(null);
		// Set passwordSet flag to true when password is not null (password can be empty)
		String password = entity.getPassword();
		vo.setPasswordSet(password != null);
		return vo;
	}

	/**
	 * Test database connection with provided configuration
	 */
	public boolean testConnection(DatasourceConfigVO vo) {
		if (dataSourceService == null) {
			logger.warn("DataSourceService not available for connection testing");
			return false;
		}
		return dataSourceService.testConnection(vo.getUrl(), vo.getUsername(), vo.getPassword(),
				vo.getDriverClassName());
	}

	/**
	 * Map VO to Entity
	 */
	private DatasourceConfigEntity mapToEntity(DatasourceConfigVO vo) {
		DatasourceConfigEntity entity = new DatasourceConfigEntity();
		BeanUtils.copyProperties(vo, entity);
		return entity;
	}

}

