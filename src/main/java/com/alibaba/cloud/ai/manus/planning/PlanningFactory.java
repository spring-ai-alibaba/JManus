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

package com.alibaba.cloud.ai.manus.planning;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.util.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.alibaba.cloud.ai.manus.agent.ToolCallbackProvider;
import com.alibaba.cloud.ai.manus.config.ManusProperties;
import com.alibaba.cloud.ai.manus.cron.service.CronService;
import com.alibaba.cloud.ai.manus.llm.LlmService;
import com.alibaba.cloud.ai.manus.llm.StreamingResponseHandler;
import com.alibaba.cloud.ai.manus.mcp.model.vo.McpServiceEntity;
import com.alibaba.cloud.ai.manus.mcp.model.vo.McpTool;
import com.alibaba.cloud.ai.manus.mcp.service.McpService;
import com.alibaba.cloud.ai.manus.mcp.service.McpStateHolderService;
import com.alibaba.cloud.ai.manus.planning.service.PlanFinalizer;
import com.alibaba.cloud.ai.manus.recorder.service.PlanExecutionRecorder;
import com.alibaba.cloud.ai.manus.runtime.executor.ImageRecognitionExecutorPool;
import com.alibaba.cloud.ai.manus.runtime.service.PlanIdDispatcher;
import com.alibaba.cloud.ai.manus.subplan.service.SubplanToolService;
import com.alibaba.cloud.ai.manus.tool.FormInputTool;
import com.alibaba.cloud.ai.manus.tool.TerminateTool;
import com.alibaba.cloud.ai.manus.tool.ToolCallBiFunctionDef;
import com.alibaba.cloud.ai.manus.tool.bash.Bash;
import com.alibaba.cloud.ai.manus.tool.browser.BrowserUseTool;
import com.alibaba.cloud.ai.manus.tool.browser.ChromeDriverService;
import com.alibaba.cloud.ai.manus.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.manus.tool.convertToMarkdown.ImageOcrProcessor;
import com.alibaba.cloud.ai.manus.tool.convertToMarkdown.MarkdownConverterTool;
import com.alibaba.cloud.ai.manus.tool.convertToMarkdown.PdfOcrProcessor;
import com.alibaba.cloud.ai.manus.tool.cron.CronTool;
import com.alibaba.cloud.ai.manus.tool.database.DataSourceService;
import com.alibaba.cloud.ai.manus.tool.database.DatabaseMetadataTool;
import com.alibaba.cloud.ai.manus.tool.database.DatabaseReadTool;
import com.alibaba.cloud.ai.manus.tool.database.DatabaseWriteTool;
import com.alibaba.cloud.ai.manus.tool.database.UuidGenerateTool;
import com.alibaba.cloud.ai.manus.tool.dirOperator.DirectoryOperator;
import com.alibaba.cloud.ai.manus.tool.excelProcessor.IExcelProcessingService;
import com.alibaba.cloud.ai.manus.tool.filesystem.UnifiedDirectoryManager;
import com.alibaba.cloud.ai.manus.tool.innerStorage.SmartContentSavingService;
import com.alibaba.cloud.ai.manus.tool.jsxGenerator.JsxGeneratorOperator;
import com.alibaba.cloud.ai.manus.tool.mapreduce.ParallelExecutionTool;
import com.alibaba.cloud.ai.manus.tool.pptGenerator.PptGeneratorOperator;
import com.alibaba.cloud.ai.manus.tool.tableProcessor.TableProcessingService;
import com.alibaba.cloud.ai.manus.tool.textOperator.TextFileOperator;
import com.alibaba.cloud.ai.manus.tool.textOperator.TextFileService;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author yuluo
 * @author <a href="mailto:yuluo08290126@gmail.com">yuluo</a>
 */

@Service
public class PlanningFactory {

	private final ChromeDriverService chromeDriverService;

	private final PlanExecutionRecorder recorder;

	private final ManusProperties manusProperties;

	private final TextFileService textFileService;

	private final SmartContentSavingService innerStorageService;

	private final UnifiedDirectoryManager unifiedDirectoryManager;

	private final DataSourceService dataSourceService;

	// private final TableProcessingService tableProcessingService; // Currently unused -
	// commented out for future use

	private final static Logger log = LoggerFactory.getLogger(PlanningFactory.class);

	private final McpService mcpService;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	@Lazy
	private LlmService llmService;

	@Autowired
	@Lazy
	private ToolCallingManager toolCallingManager;

	@Autowired
	private StreamingResponseHandler streamingResponseHandler;

	@Autowired
	@Lazy
	private CronService cronService;

	@Autowired
	private SubplanToolService subplanToolService;

	@SuppressWarnings("unused")
	@Autowired
	private PptGeneratorOperator pptGeneratorOperator;

	@Autowired
	private PlanIdDispatcher planIdDispatcher;

	@Value("${agent.init}")
	private Boolean agentInit = true;

	@SuppressWarnings("unused")
	@Autowired
	private JsxGeneratorOperator jsxGeneratorOperator;

	@SuppressWarnings("unused")
	@Autowired
	private ApplicationContext applicationContext;

	public PlanningFactory(ChromeDriverService chromeDriverService, PlanExecutionRecorder recorder,
			ManusProperties manusProperties, TextFileService textFileService, McpService mcpService,
			SmartContentSavingService innerStorageService, UnifiedDirectoryManager unifiedDirectoryManager,
			DataSourceService dataSourceService, TableProcessingService tableProcessingService,
			IExcelProcessingService excelProcessingService) {
		this.chromeDriverService = chromeDriverService;
		this.recorder = recorder;
		this.manusProperties = manusProperties;
		this.textFileService = textFileService;
		this.mcpService = mcpService;
		this.innerStorageService = innerStorageService;
		this.unifiedDirectoryManager = unifiedDirectoryManager;
		this.dataSourceService = dataSourceService;
		// this.tableProcessingService = tableProcessingService; // Currently unused
	}

	/**
	 * Create a PlanFinalizer instance
	 * @return configured PlanFinalizer instance
	 */
	public PlanFinalizer createPlanFinalizer() {
		return new PlanFinalizer(llmService, recorder, manusProperties, streamingResponseHandler);
	}

	public static class ToolCallBackContext {

		private final ToolCallback toolCallback;

		private final ToolCallBiFunctionDef<?> functionInstance;

		public ToolCallBackContext(ToolCallback toolCallback, ToolCallBiFunctionDef<?> functionInstance) {
			this.toolCallback = toolCallback;
			this.functionInstance = functionInstance;
		}

		public ToolCallback getToolCallback() {
			return toolCallback;
		}

		public ToolCallBiFunctionDef<?> getFunctionInstance() {
			return functionInstance;
		}

	}

	public Map<String, ToolCallBackContext> toolCallbackMap(String planId, String rootPlanId,
			String expectedReturnInfo) {

		Boolean infiniteContextEnabled = manusProperties.getInfiniteContextEnabled();
		Map<String, ToolCallBackContext> toolCallbackMap = new HashMap<>();
		List<ToolCallBiFunctionDef<?>> toolDefinitions = new ArrayList<>();
		if (chromeDriverService == null) {
			log.error("ChromeDriverService is null, skipping BrowserUseTool registration");
			return toolCallbackMap;
		}
		if (innerStorageService == null) {
			log.error("SmartContentSavingService is null, skipping BrowserUseTool registration");
			return toolCallbackMap;
		}
		if (agentInit) {
			// Add all tool definitions
			toolDefinitions.add(BrowserUseTool.getInstance(chromeDriverService, innerStorageService, objectMapper));
			toolDefinitions.add(DatabaseReadTool.getInstance(dataSourceService, objectMapper));
			toolDefinitions.add(DatabaseWriteTool.getInstance(dataSourceService, objectMapper));
			toolDefinitions.add(DatabaseMetadataTool.getInstance(dataSourceService, objectMapper));
			toolDefinitions.add(UuidGenerateTool.getInstance(objectMapper));
			toolDefinitions.add(new TerminateTool(planId, expectedReturnInfo));
			toolDefinitions.add(new Bash(unifiedDirectoryManager, objectMapper));
			// toolDefinitions.add(new DocLoaderTool());
			toolDefinitions.add(new TextFileOperator(textFileService, innerStorageService, objectMapper));
			toolDefinitions.add(new DirectoryOperator(unifiedDirectoryManager, objectMapper));
			// toolDefinitions.add(new UploadedFileLoaderTool(unifiedDirectoryManager,
			// applicationContext));
			// toolDefinitions.add(new TableProcessorTool(tableProcessingService));
			// toolDefinitions.add(pptGeneratorOperator);
			// toolDefinitions.add(jsxGeneratorOperator);
			// toolDefinitions.add(new FileMergeTool(unifiedDirectoryManager));
			// toolDefinitions.add(new GoogleSearch());
			// toolDefinitions.add(new PythonExecute());
			toolDefinitions.add(new FormInputTool(objectMapper));
			toolDefinitions.add(new ParallelExecutionTool(objectMapper, toolCallbackMap, planIdDispatcher));
			if (infiniteContextEnabled) {

			}
			toolDefinitions.add(new CronTool(cronService, objectMapper));
			toolDefinitions.add(new MarkdownConverterTool(unifiedDirectoryManager,
					new PdfOcrProcessor(unifiedDirectoryManager, llmService, manusProperties,
							new ImageRecognitionExecutorPool(manusProperties)),
					new ImageOcrProcessor(unifiedDirectoryManager, llmService, manusProperties,
							new ImageRecognitionExecutorPool(manusProperties))));
			// toolDefinitions.add(new ExcelProcessorTool(excelProcessingService));
		}
		else {
			toolDefinitions.add(new TerminateTool(planId, expectedReturnInfo));
		}

		List<McpServiceEntity> functionCallbacks = mcpService.getFunctionCallbacks(planId);
		for (McpServiceEntity toolCallback : functionCallbacks) {
			String serviceGroup = toolCallback.getServiceGroup();
			ToolCallback[] tCallbacks = toolCallback.getAsyncMcpToolCallbackProvider().getToolCallbacks();
			for (ToolCallback tCallback : tCallbacks) {
				// The serviceGroup is the name of the tool
				toolDefinitions.add(new McpTool(tCallback, serviceGroup, planId, new McpStateHolderService(),
						innerStorageService, objectMapper));
			}
		}
		// Create FunctionToolCallback for each tool
		for (ToolCallBiFunctionDef<?> toolDefinition : toolDefinitions) {

			try {
				FunctionToolCallback<?, ToolExecuteResult> functionToolcallback = FunctionToolCallback
					.builder(toolDefinition.getName(), toolDefinition)
					.description(toolDefinition.getDescription())
					.inputSchema(toolDefinition.getParameters())
					.inputType(toolDefinition.getInputType())
					.toolMetadata(ToolMetadata.builder().returnDirect(toolDefinition.isReturnDirect()).build())
					.build();
				toolDefinition.setCurrentPlanId(planId);
				toolDefinition.setRootPlanId(rootPlanId);
				log.info("Registering tool: {}", toolDefinition.getName());
				ToolCallBackContext functionToolcallbackContext = new ToolCallBackContext(functionToolcallback,
						toolDefinition);
				toolCallbackMap.put(toolDefinition.getName(), functionToolcallbackContext);
			}
			catch (Exception e) {
				log.error("Failed to register tool: {} - {}", toolDefinition.getName(), e.getMessage(), e);
			}
		}

		// Add subplan tool registration
		if (subplanToolService != null) {
			try {
				Map<String, PlanningFactory.ToolCallBackContext> subplanToolCallbacks = subplanToolService
					.createSubplanToolCallbacks(planId, rootPlanId, expectedReturnInfo);
				toolCallbackMap.putAll(subplanToolCallbacks);
				log.info("Registered {} subplan tools", subplanToolCallbacks.size());
			}
			catch (Exception e) {
				log.warn("Failed to register subplan tools: {}", e.getMessage());
			}
		}

		return toolCallbackMap;
	}

	@SuppressWarnings("deprecation")
	@Bean
	public RestClient.Builder createRestClient() {
		// Create RequestConfig and set the timeout (10 minutes for all timeouts)
		RequestConfig requestConfig = RequestConfig.custom()
			.setConnectTimeout(Timeout.of(10, TimeUnit.MINUTES)) // Set the connection
																	// timeout
			.setResponseTimeout(Timeout.of(10, TimeUnit.MINUTES))
			.setConnectionRequestTimeout(Timeout.of(10, TimeUnit.MINUTES))
			.build();

		// Create CloseableHttpClient and apply the configuration
		HttpClient httpClient = HttpClients.custom().setDefaultRequestConfig(requestConfig).build();

		// Use HttpComponentsClientHttpRequestFactory to wrap HttpClient
		HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);

		// Create RestClient and set the request factory
		return RestClient.builder().requestFactory(requestFactory);
	}

	/**
	 * Provides an empty ToolCallbackProvider implementation when MCP is disabled
	 */
	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(name = "spring.ai.mcp.client.enabled", havingValue = "false")
	public ToolCallbackProvider emptyToolCallbackProvider() {
		return () -> new HashMap<String, PlanningFactory.ToolCallBackContext>();
	}

}
