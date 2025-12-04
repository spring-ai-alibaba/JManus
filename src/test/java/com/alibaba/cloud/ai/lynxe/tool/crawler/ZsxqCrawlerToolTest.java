package com.alibaba.cloud.ai.lynxe.tool.crawler;

import com.alibaba.cloud.ai.lynxe.tool.browser.ChromeDriverService;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 知识星球爬虫工具测试
 * 注意: 这些测试需要真实的浏览器环境和网络连接
 */
@SpringBootTest
@Disabled("需要真实浏览器环境,CI环境中跳过")
public class ZsxqCrawlerToolTest {

	@Autowired
	private ChromeDriverService chromeDriverService;

	@Autowired
	private UnifiedDirectoryManager unifiedDirectoryManager;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private ToolI18nService toolI18nService;

	private ZsxqCrawlerTool tool;

	@BeforeEach
	public void setUp() {
		tool = new ZsxqCrawlerTool(chromeDriverService, unifiedDirectoryManager, objectMapper, toolI18nService);
		tool.setCurrentPlanId("test-plan-001");
		tool.setRootPlanId("test-plan-001");
	}

	@Test
	public void testLogin() {
		ZsxqCrawlerRequestVO request = new ZsxqCrawlerRequestVO();
		request.setAction("login");

		ToolExecuteResult result = tool.run(request);
		assertNotNull(result);
		assertTrue(result.getOutput().contains("登录"));
	}

	@Test
	public void testCrawlPost() {
		ZsxqCrawlerRequestVO request = new ZsxqCrawlerRequestVO();
		request.setAction("crawl_post");
		request.setGroupId("552521181154");
		request.setMaxPosts(5);

		ToolExecuteResult result = tool.run(request);
		assertNotNull(result);
		System.out.println("Crawl result: " + result.getOutput());
	}

	@Test
	public void testDownloadAttachments() {
		ZsxqCrawlerRequestVO request = new ZsxqCrawlerRequestVO();
		request.setAction("download_attachments");
		request.setGroupId("552521181154");
		request.setTopicId("22811885514812441"); // 替换为实际的帖子ID

		ToolExecuteResult result = tool.run(request);
		assertNotNull(result);
		System.out.println("Download result: " + result.getOutput());
	}

	@Test
	public void testToolMetadata() {
		assertEquals("zsxq_crawler", tool.getName());
		assertNotNull(tool.getDescription());
		assertNotNull(tool.getParameters());
		assertTrue(tool.isSelectable());
	}

}
