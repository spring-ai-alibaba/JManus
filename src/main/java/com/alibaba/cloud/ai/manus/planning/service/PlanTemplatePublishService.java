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
package com.alibaba.cloud.ai.manus.planning.service;

import com.alibaba.cloud.ai.manus.planning.model.po.PlanTemplate;
import com.alibaba.cloud.ai.manus.planning.repository.PlanTemplateRepository;
import com.alibaba.cloud.ai.manus.coordinator.entity.vo.CoordinatorToolVO;
import com.alibaba.cloud.ai.manus.coordinator.service.CoordinatorToolServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for publishing plan templates as inner toolcalls
 */
@Service
public class PlanTemplatePublishService {

    private static final Logger log = LoggerFactory.getLogger(PlanTemplatePublishService.class);

    @Autowired
    private PlanTemplateRepository planTemplateRepository;

    @Autowired
    private CoordinatorToolServiceImpl coordinatorToolService;

    // Track registered plan templates
    private final Set<String> registeredPlanTemplates = ConcurrentHashMap.newKeySet();

    /**
     * Register specific plan templates as inner toolcalls
     * @param planNames List of plan names to register
     * @return Registration result
     */
    @Transactional
    public Map<String, Object> registerPlanTemplatesAsToolcalls(List<String> planNames) {
        Map<String, Object> result = new HashMap<>();
        List<String> successList = new ArrayList<>();
        List<String> errorList = new ArrayList<>();
        Map<String, String> errors = new HashMap<>();

        log.info("Starting registration of plan templates as inner toolcalls: {}", planNames);

        for (String planName : planNames) {
            try {
                // Find plan template by planTemplateId (assuming planName matches planTemplateId pattern)
                Optional<PlanTemplate> templateOpt = planTemplateRepository.findByPlanTemplateId(planName);
                
                if (templateOpt.isEmpty()) {
                    // Try to find by title containing the plan name
                    List<PlanTemplate> allTemplates = planTemplateRepository.findAll();
                    List<PlanTemplate> matchingTemplates = allTemplates.stream()
                        .filter(t -> t.getTitle().toLowerCase().contains(planName.toLowerCase()))
                        .collect(java.util.stream.Collectors.toList());
                    
                    if (matchingTemplates.isEmpty()) {
                        String error = "Plan template not found: " + planName;
                        errorList.add(planName);
                        errors.put(planName, error);
                        log.warn(error);
                        continue;
                    }
                    
                    // Register each found template
                    for (PlanTemplate template : matchingTemplates) {
                        if (registerSinglePlanTemplate(template)) {
                            successList.add(template.getPlanTemplateId());
                            registeredPlanTemplates.add(template.getPlanTemplateId());
                            log.info("Successfully registered plan template: {} (ID: {})", planName, template.getPlanTemplateId());
                        } else {
                            String error = "Failed to register plan template: " + template.getPlanTemplateId();
                            errorList.add(template.getPlanTemplateId());
                            errors.put(template.getPlanTemplateId(), error);
                            log.error(error);
                        }
                    }
                } else {
                    // Register the found template
                    PlanTemplate template = templateOpt.get();
                    if (registerSinglePlanTemplate(template)) {
                        successList.add(template.getPlanTemplateId());
                        registeredPlanTemplates.add(template.getPlanTemplateId());
                        log.info("Successfully registered plan template: {} (ID: {})", planName, template.getPlanTemplateId());
                    } else {
                        String error = "Failed to register plan template: " + template.getPlanTemplateId();
                        errorList.add(template.getPlanTemplateId());
                        errors.put(template.getPlanTemplateId(), error);
                        log.error(error);
                    }
                }

            } catch (Exception e) {
                String error = "Error registering plan template " + planName + ": " + e.getMessage();
                errorList.add(planName);
                errors.put(planName, error);
                log.error(error, e);
            }
        }

        result.put("success", errorList.isEmpty());
        result.put("totalRequested", planNames.size());
        result.put("successCount", successList.size());
        result.put("errorCount", errorList.size());
        result.put("successList", successList);
        result.put("errorList", errorList);
        result.put("errors", errors);
        result.put("message", String.format("Registered %d out of %d plan templates", successList.size(), planNames.size()));

        log.info("Plan template registration completed: {}/{} successful", successList.size(), planNames.size());
        return result;
    }

    /**
     * Register a single plan template as inner toolcall using CoordinatorToolService
     * @param planTemplate Plan template to register
     * @return true if successful, false otherwise
     */
    private boolean registerSinglePlanTemplate(PlanTemplate planTemplate) {
        try {
            // Create CoordinatorToolVO for the plan template
            CoordinatorToolVO coordinatorToolVO = new CoordinatorToolVO();
            coordinatorToolVO.setToolName("tool_" + planTemplate.getTitle());
            coordinatorToolVO.setToolDescription(planTemplate.getUserRequest());
            coordinatorToolVO.setPlanTemplateId(planTemplate.getPlanTemplateId());
            coordinatorToolVO.setServiceGroup("inited-toolcall");
            
            // Enable internal toolcall service
            coordinatorToolVO.setEnableInternalToolcall(true);
            coordinatorToolVO.setEnableHttpService(false);
            coordinatorToolVO.setEnableMcpService(false);
            
            // Set default input schema for plan templates
            coordinatorToolVO.setInputSchema("[]");
            coordinatorToolVO.setPublishStatus("PUBLISHED");

            // Create coordinator tool using the service
            CoordinatorToolVO createdTool = coordinatorToolService.createCoordinatorTool(coordinatorToolVO);
            
            log.debug("Created CoordinatorTool for plan template: {} with ID: {}", 
                    planTemplate.getPlanTemplateId(), createdTool.getId());
            return true;

        } catch (Exception e) {
            log.error("Failed to register plan template as coordinator tool: {}", planTemplate.getPlanTemplateId(), e);
            return false;
        }
    }

    /**
     * Unregister plan templates from inner toolcalls
     * @param planNames List of plan names to unregister
     * @return Unregistration result
     */
    @Transactional
    public Map<String, Object> unregisterPlanTemplatesAsToolcalls(List<String> planNames) {
        Map<String, Object> result = new HashMap<>();
        List<String> successList = new ArrayList<>();
        List<String> errorList = new ArrayList<>();
        Map<String, String> errors = new HashMap<>();

        log.info("Starting unregistration of plan templates from inner toolcalls: {}", planNames);

        for (String planName : planNames) {
            try {
                // Find plan template by planTemplateId (assuming planName matches planTemplateId pattern)
                Optional<PlanTemplate> templateOpt = planTemplateRepository.findByPlanTemplateId(planName);
                
                if (templateOpt.isEmpty()) {
                    // Try to find by title containing the plan name
                    List<PlanTemplate> allTemplates = planTemplateRepository.findAll();
                    List<PlanTemplate> matchingTemplates = allTemplates.stream()
                        .filter(t -> t.getTitle().toLowerCase().contains(planName.toLowerCase()))
                        .collect(java.util.stream.Collectors.toList());
                    
                    if (matchingTemplates.isEmpty()) {
                        String error = "Plan template not found: " + planName;
                        errorList.add(planName);
                        errors.put(planName, error);
                        log.warn(error);
                        continue;
                    }
                    
                    // Unregister each found template
                    for (PlanTemplate template : matchingTemplates) {
                        if (unregisterSinglePlanTemplate(template)) {
                            successList.add(template.getPlanTemplateId());
                            registeredPlanTemplates.remove(template.getPlanTemplateId());
                            log.info("Successfully unregistered plan template: {} (ID: {})", planName, template.getPlanTemplateId());
                        } else {
                            String error = "Failed to unregister plan template: " + template.getPlanTemplateId();
                            errorList.add(template.getPlanTemplateId());
                            errors.put(template.getPlanTemplateId(), error);
                            log.error(error);
                        }
                    }
                } else {
                    // Unregister the found template
                    PlanTemplate template = templateOpt.get();
                    if (unregisterSinglePlanTemplate(template)) {
                        successList.add(template.getPlanTemplateId());
                        registeredPlanTemplates.remove(template.getPlanTemplateId());
                        log.info("Successfully unregistered plan template: {} (ID: {})", planName, template.getPlanTemplateId());
                    } else {
                        String error = "Failed to unregister plan template: " + template.getPlanTemplateId();
                        errorList.add(template.getPlanTemplateId());
                        errors.put(template.getPlanTemplateId(), error);
                        log.error(error);
                    }
                }

            } catch (Exception e) {
                String error = "Error unregistering plan template " + planName + ": " + e.getMessage();
                errorList.add(planName);
                errors.put(planName, error);
                log.error(error, e);
            }
        }

        result.put("success", errorList.isEmpty());
        result.put("totalRequested", planNames.size());
        result.put("successCount", successList.size());
        result.put("errorCount", errorList.size());
        result.put("successList", successList);
        result.put("errorList", errorList);
        result.put("errors", errors);
        result.put("message", String.format("Unregistered %d out of %d plan templates", successList.size(), planNames.size()));

        log.info("Plan template unregistration completed: {}/{} successful", successList.size(), planNames.size());
        return result;
    }

    /**
     * Unregister a single plan template from inner toolcalls using CoordinatorToolService
     * @param planTemplate Plan template to unregister
     * @return true if successful, false otherwise
     */
    private boolean unregisterSinglePlanTemplate(PlanTemplate planTemplate) {
        try {
            // Find the corresponding CoordinatorTool by planTemplateId
            Optional<CoordinatorToolVO> coordinatorToolOpt = coordinatorToolService.getCoordinatorToolByPlanTemplateId(planTemplate.getPlanTemplateId());
            
            if (coordinatorToolOpt.isPresent()) {
                CoordinatorToolVO coordinatorTool = coordinatorToolOpt.get();
                coordinatorToolService.deleteCoordinatorTool(coordinatorTool.getId());
                log.debug("Deleted CoordinatorTool for plan template: {} with ID: {}", 
                        planTemplate.getPlanTemplateId(), coordinatorTool.getId());
                return true;
            } else {
                log.warn("No CoordinatorTool found for plan template: {}", planTemplate.getPlanTemplateId());
                return false;
            }

        } catch (Exception e) {
            log.error("Failed to unregister plan template from coordinator tools: {}", planTemplate.getPlanTemplateId(), e);
            return false;
        }
    }

    /**
     * Get registration status
     * @return Registration status information
     */
    public Map<String, Object> getRegistrationStatus() {
        Map<String, Object> status = new HashMap<>();
        
        try {
            // Get all plan templates
            List<PlanTemplate> allTemplates = planTemplateRepository.findAll();
            
            // Get registered coordinator tools
            List<CoordinatorToolVO> registeredTools = coordinatorToolService.getAllCoordinatorTools();
            Set<String> registeredPlanTemplateIds = new HashSet<>();
            for (CoordinatorToolVO tool : registeredTools) {
                if (tool.getPlanTemplateId() != null) {
                    registeredPlanTemplateIds.add(tool.getPlanTemplateId());
                }
            }

            status.put("totalPlanTemplates", allTemplates.size());
            status.put("registeredPlanTemplates", registeredPlanTemplateIds.size());
            status.put("unregisteredPlanTemplates", allTemplates.size() - registeredPlanTemplateIds.size());
            status.put("registeredPlanTemplateIds", new ArrayList<>(registeredPlanTemplateIds));
            status.put("success", true);

        } catch (Exception e) {
            log.error("Failed to get registration status", e);
            status.put("success", false);
            status.put("error", e.getMessage());
        }

        return status;
    }

    /**
     * Get all registered plan templates
     * @return List of registered plan templates
     */
    public Map<String, Object> getRegisteredPlanTemplates() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            List<CoordinatorToolVO> registeredTools = coordinatorToolService.getAllCoordinatorTools();
            List<Map<String, Object>> registeredTemplates = new ArrayList<>();

            for (CoordinatorToolVO tool : registeredTools) {
                if (tool.getPlanTemplateId() != null) {
                    Map<String, Object> templateInfo = new HashMap<>();
                    templateInfo.put("toolId", tool.getId());
                    templateInfo.put("toolName", tool.getToolName());
                    templateInfo.put("planTemplateId", tool.getPlanTemplateId());
                    templateInfo.put("description", tool.getToolDescription());
                    templateInfo.put("serviceGroup", tool.getServiceGroup());
                    templateInfo.put("enableInternalToolcall", tool.getEnableInternalToolcall());
                    templateInfo.put("enableHttpService", tool.getEnableHttpService());
                    templateInfo.put("enableMcpService", tool.getEnableMcpService());
                    templateInfo.put("mcpEndpoint", tool.getMcpEndpoint());
                    templateInfo.put("publishStatus", tool.getPublishStatus());
                    registeredTemplates.add(templateInfo);
                }
            }

            result.put("success", true);
            result.put("registeredTemplates", registeredTemplates);
            result.put("count", registeredTemplates.size());

        } catch (Exception e) {
            log.error("Failed to get registered plan templates", e);
            result.put("success", false);
            result.put("error", e.getMessage());
        }

        return result;
    }

    /**
     * Check if a plan template is registered
     * @param planTemplateId Plan template ID to check
     * @return true if registered, false otherwise
     */
    public boolean isPlanTemplateRegistered(String planTemplateId) {
        return registeredPlanTemplates.contains(planTemplateId);
    }

    /**
     * Get count of registered plan templates
     * @return Number of registered plan templates
     */
    public int getRegisteredCount() {
        return registeredPlanTemplates.size();
    }
}
