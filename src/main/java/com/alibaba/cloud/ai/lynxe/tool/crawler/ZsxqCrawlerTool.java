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

import com.alibaba.cloud.ai.lynxe.tool.AbstractBaseTool;
import com.alibaba.cloud.ai.lynxe.tool.browser.ChromeDriverService;
import com.alibaba.cloud.ai.lynxe.tool.browser.DriverWrapper;
import com.alibaba.cloud.ai.lynxe.tool.code.ToolExecuteResult;
import com.alibaba.cloud.ai.lynxe.tool.filesystem.UnifiedDirectoryManager;
import com.alibaba.cloud.ai.lynxe.tool.i18n.ToolI18nService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Download;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Response;
import com.microsoft.playwright.options.WaitUntilState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 知识星球爬虫工具
 * 支持爬取帖子内容、图片、附件等
 */
public class ZsxqCrawlerTool extends AbstractBaseTool<ZsxqCrawlerRequestVO> {

	private static final Logger log = LoggerFactory.getLogger(ZsxqCrawlerTool.class);

	private final ChromeDriverService chromeDriverService;

	private final UnifiedDirectoryManager unifiedDirectoryManager;

	private final ObjectMapper objectMapper;

	private final ToolI18nService toolI18nService;

	private static final String ZSXQ_BASE_URL = "https://wx.zsxq.com";

	public ZsxqCrawlerTool(ChromeDriverService chromeDriverService,
			UnifiedDirectoryManager unifiedDirectoryManager, ObjectMapper objectMapper,
			ToolI18nService toolI18nService) {
		this.chromeDriverService = chromeDriverService;
		this.unifiedDirectoryManager = unifiedDirectoryManager;
		this.objectMapper = objectMapper;
		this.toolI18nService = toolI18nService;
	}

	@Override
	public String getName() {
		return "zsxq_crawler";
	}

	@Override
	public String getDescription() {
		return toolI18nService.getDescription("zsxq-crawler");
	}

	@Override
	public String getParameters() {
		return toolI18nService.getParameters("zsxq-crawler");
	}

	@Override
	public Class<ZsxqCrawlerRequestVO> getInputType() {
		return ZsxqCrawlerRequestVO.class;
	}

	@Override
	public ToolExecuteResult run(ZsxqCrawlerRequestVO requestVO) {
		String action = requestVO.getAction();
		if (action == null || action.isEmpty()) {
			return new ToolExecuteResult("Action parameter is required");
		}

		try {
			DriverWrapper driver = chromeDriverService.getDriver(currentPlanId);
			Page page = driver.getCurrentPage();

			switch (action) {
				case "login":
					return handleLogin(page);
				case "crawl_post":
					return handleCrawlPost(page, requestVO);
				case "download_attachments":
					return handleDownloadAttachments(page, requestVO);
				default:
					return new ToolExecuteResult("Unknown action: " + action);
			}
		}
		catch (Exception e) {
			log.error("Error executing zsxq_crawler: {}", e.getMessage(), e);
			return new ToolExecuteResult("Error: " + e.getMessage());
		}
	}

	/**
	 * 处理登录操作
	 */
	private ToolExecuteResult handleLogin(Page page) {
		try {
			// 导航到知识星球登录页
			page.navigate(ZSXQ_BASE_URL + "/dweb2/login", new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
			
			// 等待用户手动登录(显示二维码或密码登录)
			String loginInfo = """
					已打开知识星球登录页面,请在浏览器中完成登录操作。
					
					登录方式:
					1. 扫描二维码登录
					2. 使用手机号+密码登录
					
					登录成功后,Cookie会自动保存,下次爬取时可直接使用。
					
					提示: 请确认登录成功后再执行爬取操作。
					""";
			
			return new ToolExecuteResult(loginInfo);
		}
		catch (Exception e) {
			log.error("Login failed: {}", e.getMessage(), e);
			return new ToolExecuteResult("Login failed: " + e.getMessage());
		}
	}

	/**
	 * 爬取帖子内容
	 */
	private ToolExecuteResult handleCrawlPost(Page page, ZsxqCrawlerRequestVO requestVO) {
		try {
			String groupId = requestVO.getGroupId();
			if (groupId == null || groupId.isEmpty()) {
				return new ToolExecuteResult("group_id is required for crawl_post action");
			}

			// 导航到星球主页 - 使用正确的URL格式
			String groupUrl = ZSXQ_BASE_URL + "/dweb2/index/group/" + groupId;
			log.info("Navigating to group URL: {}", groupUrl);
			page.navigate(groupUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));

			// 等待动态内容加载(增加等待时间)
			page.waitForTimeout(5000);
			
			// 检查是否需要登录
			String currentUrl = page.url();
			if (currentUrl.contains("/login")) {
				return new ToolExecuteResult("请先执行登录操作 (action: login),登录成功后再爬取内容");
			}
			
			// 尝试多个可能的选择器 - 增加超时时间
			boolean pageLoaded = false;
			String[] selectors = {
				".topic-list",
				"[class*='topic']",
				"[class*='feed']",
				".main-content",
				"#app"
			};
			
			for (String selector : selectors) {
				try {
					page.waitForSelector(selector, new Page.WaitForSelectorOptions().setTimeout(5000));
					log.info("Found selector: {}", selector);
					pageLoaded = true;
					break;
				} catch (Exception e) {
					log.debug("Selector not found: {}", selector);
				}
			}
			
			if (!pageLoaded) {
				// 尝试获取页面HTML调试信息
				String bodyText = page.locator("body").textContent();
				log.warn("Page loaded but no known selectors found. Body preview: {}", 
					bodyText.substring(0, Math.min(200, bodyText.length())));
				return new ToolExecuteResult("页面加载成功但未找到帖子列表,可能需要登录或页面结构已变化。当前URL: " + currentUrl);
			}
			
			// 再等待一段时间确保内容加载
			page.waitForTimeout(2000);

			// 提取帖子列表
			List<Map<String, Object>> posts = extractPosts(page, requestVO.getMaxPosts() != null ? requestVO.getMaxPosts() : 10);

			Map<String, Object> result = new HashMap<>();
			result.put("group_id", groupId);
			result.put("posts_count", posts.size());
			result.put("posts", posts);

			return new ToolExecuteResult(objectMapper.writeValueAsString(result));
		}
		catch (Exception e) {
			log.error("Crawl post failed: {}", e.getMessage(), e);
			return new ToolExecuteResult("Crawl post failed: " + e.getMessage());
		}
	}

	/**
	 * 下载附件
	 */
	private ToolExecuteResult handleDownloadAttachments(Page page, ZsxqCrawlerRequestVO requestVO) {
		try {
			String groupId = requestVO.getGroupId();
			String topicId = requestVO.getTopicId();
			
			if (groupId == null || topicId == null) {
				return new ToolExecuteResult("group_id and topic_id are required for download_attachments action");
			}

			// 导航到帖子详情页 - 使用正确的URL格式
			String topicUrl = ZSXQ_BASE_URL + "/group/" + groupId + "/topic/" + topicId;
			log.info("Navigating to topic URL: {}", topicUrl);
			page.navigate(topicUrl, new Page.NavigateOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));

			// 等待内容加载
			page.waitForTimeout(5000);

			// 查找所有附件链接
			List<String> attachmentUrls = extractAttachmentUrls(page);

			if (attachmentUrls.isEmpty()) {
				// 输出页面调试信息帮助排查
				String pageInfo = String.format(
					"当前页面未找到附件。\n" +
					"- 页面URL: %s\n" +
					"- 页面标题: %s\n" +
					"请检查: 1)是否已登录 2)topic_id是否正确 3)帖子是否确实有附件",
					page.url(), page.title()
				);
				return new ToolExecuteResult(pageInfo);
			}

			// 创建下载目录
			Path downloadDir = getDownloadDirectory(requestVO.getDownloadPath(), groupId, topicId);
			
			// 诊断：输出页面HTML结构帮助定位音频元素
			try {
				String pageHtml = page.content();
				log.info("=== 页面HTML诊断 ===");
				
				// 查找可能包含音频的关键HTML片段
				String[] keywords = {"mp3", "audio", "file", "attachment", "阶段简"};
				for (String keyword : keywords) {
					if (pageHtml.toLowerCase().contains(keyword.toLowerCase())) {
						int index = pageHtml.toLowerCase().indexOf(keyword.toLowerCase());
						int start = Math.max(0, index - 200);
						int end = Math.min(pageHtml.length(), index + 200);
						String snippet = pageHtml.substring(start, end);
						log.info("Found '{}' in HTML: ...{}...", keyword, snippet);
					}
				}
				
				// 保存完整HTML到文件以便详细分析
				try {
					Path htmlDebugFile = downloadDir.resolve("page_debug.html");
					java.nio.file.Files.writeString(htmlDebugFile, pageHtml);
					log.info("页面HTML已保存到: {}", htmlDebugFile);
				} catch (Exception fileError) {
					log.warn("Failed to save HTML file: {}", fileError.getMessage());
				}
			} catch (Exception e) {
				log.warn("Failed to output HTML debug info: {}", e.getMessage());
			}

			// 下载所有附件
			List<String> downloadedFiles = new ArrayList<>();
			for (int i = 0; i < attachmentUrls.size(); i++) {
				String url = attachmentUrls.get(i);
				String fileName = downloadAttachment(page, url, downloadDir, i);
				if (fileName != null) {
					downloadedFiles.add(fileName);
				}
			}

			Map<String, Object> result = new HashMap<>();
			result.put("topic_id", topicId);
			result.put("total_attachments", attachmentUrls.size());
			result.put("downloaded_count", downloadedFiles.size());
			result.put("download_path", downloadDir.toString());
			result.put("files", downloadedFiles);

			return new ToolExecuteResult(objectMapper.writeValueAsString(result));
		}
		catch (Exception e) {
			log.error("Download attachments failed: {}", e.getMessage(), e);
			return new ToolExecuteResult("Download attachments failed: " + e.getMessage());
		}
	}

	/**
	 * 提取帖子列表
	 */
	private List<Map<String, Object>> extractPosts(Page page, int maxPosts) {
		List<Map<String, Object>> posts = new ArrayList<>();

		try {
			// 使用JavaScript提取帖子数据
			String script = """
					() => {
					    const posts = [];
					    const topicElements = document.querySelectorAll('.topic-list .topic-item');
					    
					    topicElements.forEach((element, index) => {
					        if (index >= %d) return;
					        
					        const post = {};
					        
					        // 提取作者信息
					        const authorElement = element.querySelector('.author-name');
					        if (authorElement) {
					            post.author = authorElement.textContent.trim();
					        }
					        
					        // 提取内容
					        const contentElement = element.querySelector('.topic-content');
					        if (contentElement) {
					            post.content = contentElement.textContent.trim();
					        }
					        
					        // 提取时间
					        const timeElement = element.querySelector('.topic-time');
					        if (timeElement) {
					            post.time = timeElement.textContent.trim();
					        }
					        
					        // 提取帖子ID
					        const linkElement = element.querySelector('a[href*="/topic/"]');
					        if (linkElement) {
					            const href = linkElement.getAttribute('href');
					            const topicIdMatch = href.match(/topic\\/(\\d+)/);
					            if (topicIdMatch) {
					                post.topic_id = topicIdMatch[1];
					            }
					        }
					        
					        // 提取附件信息
					        const attachments = [];
					        const attachmentElements = element.querySelectorAll('.file-item, .audio-item');
					        attachmentElements.forEach(att => {
					            const fileName = att.querySelector('.file-name, .audio-name');
					            if (fileName) {
					                attachments.push({
					                    name: fileName.textContent.trim(),
					                    type: att.classList.contains('audio-item') ? 'audio' : 'file'
					                });
					            }
					        });
					        if (attachments.length > 0) {
					            post.attachments = attachments;
					        }
					        
					        posts.push(post);
					    });
					    
					    return posts;
					}
					""".formatted(maxPosts);

			Object result = page.evaluate(script);
			if (result instanceof List) {
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> extractedPosts = (List<Map<String, Object>>) result;
				posts.addAll(extractedPosts);
			}
		}
		catch (Exception e) {
			log.error("Failed to extract posts: {}", e.getMessage(), e);
		}

		return posts;
	}

	/**
	 * 提取附件下载链接 - 知识星球特别版：处理Angular动态组件
	 */
	private List<String> extractAttachmentUrls(Page page) {
		List<String> urls = new ArrayList<>();

		try {
			// 方法1: 直接从页面JavaScript变量或API响应中提取下载链接
			String extractScript = """
				() => {
				    const results = {
				        files: [],
				        debug: []
				    };
				    
				    // 查找文件项元素
				    const fileItems = document.querySelectorAll('.file-gallery-container .item');
				    results.debug.push(`找到 ${fileItems.length} 个文件项`);
				    
				    if (fileItems.length === 0) {
				        return results;
				    }
				    
				    // 尝试从每个文件项提取信息
				    fileItems.forEach((item, index) => {
				        const fileNameEl = item.querySelector('.file-name');
				        const fileName = fileNameEl ? fileNameEl.textContent.trim() : '';
				        
				        const fileInfo = {
				            fileName: fileName,
				            index: index
				        };
				        
				        // 尝试各种方式获取下载链接
				        // 1. 查找父元素上的属性
				        const attrs = item.getAttributeNames();
				        attrs.forEach(attr => {
				            const value = item.getAttribute(attr);
				            if (value && (value.includes('http') || value.includes('file') || value.includes('download'))) {
				                fileInfo[attr] = value;
				            }
				        });
				        
				        // 2. 查找所有链接
				        const links = item.querySelectorAll('a[href]');
				        if (links.length > 0) {
				            fileInfo.links = Array.from(links).map(a => a.href);
				        }
				        
				        // 3. 尝试从Angular组件获取数据
				        // 查找item的所有属性（包括Angular绑定的数据）
				        for (let key in item) {
				            if (key.startsWith('__ngContext__') || key.startsWith('ng')) {
				                try {
				                    const value = item[key];
				                    if (value && typeof value === 'object') {
				                        fileInfo.angularData = 'found';
				                    }
				                } catch (e) {}
				            }
				        }
				        
				        results.files.push(fileInfo);
				    });
				    
				    // 尝试查找下载按钮
				    const downloadButtons = document.querySelectorAll('[class*="download"], [class*="Download"], button:has(.icon-download)');
				    results.debug.push(`找到 ${downloadButtons.length} 个下载按钮`);
				    
				    // 查找所有可能的文件URL
				    const scripts = document.querySelectorAll('script');
				    scripts.forEach(script => {
				        const content = script.textContent || '';
				        if (content.includes('.mp3') || content.includes('.pdf') || content.includes('download')) {
				            // 提取URL模式
				            const urlMatches = content.match(/https?:\\/\\/[^\\s"']+\\.(mp3|pdf|doc|zip)/gi);
				            if (urlMatches) {
				                results.scriptUrls = urlMatches;
				            }
				        }
				    });
				    
				    return results;
				}
				""";
			
			Object extractResult = page.evaluate(extractScript);
			if (extractResult instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> resultMap = (Map<String, Object>) extractResult;
				
				@SuppressWarnings("unchecked")
				List<String> debugInfo = (List<String>) resultMap.get("debug");
				if (debugInfo != null) {
					debugInfo.forEach(info -> log.info("调试: {}", info));
				}
				
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> files = (List<Map<String, Object>>) resultMap.get("files");
				if (files != null) {
					for (Map<String, Object> file : files) {
						log.info("文件信息: {}", file);
						
						// 从文件信息中提取URL
						@SuppressWarnings("unchecked")
						List<String> links = (List<String>) file.get("links");
						if (links != null && !links.isEmpty()) {
							urls.addAll(links);
						}
					}
				}
				
				// 从脚本中提取的URL
				@SuppressWarnings("unchecked")
				List<String> scriptUrls = (List<String>) resultMap.get("scriptUrls");
				if (scriptUrls != null && !scriptUrls.isEmpty()) {
					log.info("从脚本中找到URL: {}", scriptUrls);
					urls.addAll(scriptUrls);
				}
			}
			
			// 如果还是没找到，尝试点击文件项后查找弹出的下载按钮
			if (urls.isEmpty()) {
				log.info("尝试点击文件项并查找下载按钮...");
				
				// 点击第一个文件项打开预览
				String clickAndFindDownloadButton = """
					() => {
					    const fileItems = document.querySelectorAll('.file-gallery-container .item');
					    if (fileItems.length > 0) {
					        fileItems[0].click();
					        return { clicked: true };
					    }
					    return { clicked: false };
					}
					""";
				
				page.evaluate(clickAndFindDownloadButton);
				
				// 等待弹窗出现
				page.waitForTimeout(1000);
				
				// 查找下载按钮或下载链接
				String findDownloadUrl = """
					() => {
					    const results = { urls: [], debug: [] };
					    
					    // 查找文件预览弹窗
					    const preview = document.querySelector('app-file-preview');
					    if (!preview) {
					        results.debug.push('未找到文件预览弹窗');
					        return results;
					    }
					    
					    results.debug.push('找到文件预览弹窗');
					    
					    // 查找所有可能的下载按钮或链接
					    const downloadSelectors = [
					        'a[download]',
					        'a[href*="download"]',
					        'button[class*="download"]',
					        '.download-btn',
					        '[class*="Download"]',
					        'a[href*=".mp3"]',
					        'a[href*=".pdf"]'
					    ];
					    
					    downloadSelectors.forEach(selector => {
					        const elements = preview.querySelectorAll(selector);
					        results.debug.push(`${selector}: ${elements.length}`);
					        elements.forEach(el => {
					            if (el.href) {
					                results.urls.push(el.href);
					            }
					        });
					    });
					    
					    return results;
					}
					""";
				
				Object downloadResult = page.evaluate(findDownloadUrl);
				if (downloadResult instanceof Map) {
					@SuppressWarnings("unchecked")
					Map<String, Object> dlMap = (Map<String, Object>) downloadResult;
					
					@SuppressWarnings("unchecked")
					List<String> debugInfo = (List<String>) dlMap.get("debug");
					if (debugInfo != null) {
						debugInfo.forEach(info -> log.info("下载按钮查找: {}", info));
					}
					
					@SuppressWarnings("unchecked")
					List<String> downloadUrls = (List<String>) dlMap.get("urls");
					if (downloadUrls != null && !downloadUrls.isEmpty()) {
						log.info("从弹窗中找到下载URL: {}", downloadUrls);
						urls.addAll(downloadUrls);
					}
				}
			}
			
			// 最后的降级方案：从DOM提取
			if (urls.isEmpty()) {
				log.warn("所有方法都未找到下载链接，尝试DOM提取");
				urls.addAll(extractUrlsFromDOM(page));
			}
			
		}
		catch (Exception e) {
			log.error("Failed to extract attachment URLs: {}", e.getMessage(), e);
		}

		return urls;
	}
	
	/**
	 * 从DOM提取URL（降级方案）
	 */
	private List<String> extractUrlsFromDOM(Page page) {
		List<String> urls = new ArrayList<>();

		try {
			// 使用JavaScript提取附件链接 - 优先真实附件
			String script = """
					() => {
					    const attachmentUrls = [];
					    const imageUrls = [];
					    const debugInfo = [];
					    
					    // 优先级1: 提取明确的附件（音频、文件）
					    const attachmentSelectors = [
					        '.audio-item',
					        '.file-item',
					        '[class*="AudioItem"]',
					        '[class*="FileItem"]',
					        '[class*="attachment"]',
					        'audio',
					        'video',
					        'a[href*=".mp3"]',
					        'a[href*=".mp4"]',
					        'a[href*=".pdf"]',
					        'a[href*=".doc"]',
					        'a[href*=".zip"]'
					    ];
					    
					    attachmentSelectors.forEach(selector => {
					        const elements = document.querySelectorAll(selector);
					        debugInfo.push(`Attachment selector: ${selector}, Found: ${elements.length}`);
					        
					        elements.forEach(element => {
					            // 提取href链接
					            if (element.tagName === 'A' && element.href) {
					                attachmentUrls.push(element.href);
					                debugInfo.push(`Found link: ${element.href}`);
					            }
					            // 提取子元素中的链接
					            const links = element.querySelectorAll('a[href]');
					            links.forEach(link => {
					                attachmentUrls.push(link.href);
					                debugInfo.push(`Found nested link: ${link.href}`);
					            });
					            // 提取audio/video的src
					            if (element.src) {
					                attachmentUrls.push(element.src);
					                debugInfo.push(`Found src: ${element.src}`);
					            }
					        });
					    });
					    
					    // 优先级2: 只有在没有找到附件时才提取图片
					    if (attachmentUrls.length === 0) {
					        debugInfo.push('No attachments found, falling back to images');
					        document.querySelectorAll('img[src]').forEach(img => {
					            if (img.src && !img.src.includes('avatar') && !img.src.includes('emoji')) {
					                imageUrls.push(img.src);
					            }
					        });
					    }
					    
					    // 合并结果：附件优先，没有附件才返回图片
					    const finalUrls = attachmentUrls.length > 0 ? attachmentUrls : imageUrls;
					    const uniqueUrls = [...new Set(finalUrls)];
					    
					    return {
					        urls: uniqueUrls,
					        debug: debugInfo,
					        pageTitle: document.title,
					        bodyPreview: document.body.innerText.substring(0, 300),
					        attachmentCount: attachmentUrls.length,
					        imageCount: imageUrls.length
					    };
					}
					""";

			Object result = page.evaluate(script);
			if (result instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> resultMap = (Map<String, Object>) result;
				
				// 输出调试信息
				log.info("Page title: {}", resultMap.get("pageTitle"));
				log.info("Attachment count: {}, Image count: {}", 
					resultMap.get("attachmentCount"), resultMap.get("imageCount"));
				
				@SuppressWarnings("unchecked")
				List<String> debugInfo = (List<String>) resultMap.get("debug");
				if (debugInfo != null) {
					debugInfo.forEach(info -> log.info("Extraction debug: {}", info));
				}
				
				@SuppressWarnings("unchecked")
				List<String> extractedUrls = (List<String>) resultMap.get("urls");
				if (extractedUrls != null) {
					urls.addAll(extractedUrls);
					log.info("Extracted {} URLs (prioritizing attachments over images)", urls.size());
					urls.forEach(url -> log.info("Will download: {}", url));
				}
			}
		}
		catch (Exception e) {
			log.error("Failed to extract attachment URLs: {}", e.getMessage(), e);
		}

		return urls;
	}

	/**
	 * 下载单个附件 - 改进版，避免点击头像等无关链接
	 */
	private String downloadAttachment(Page page, String url, Path downloadDir, int index) {
		try {
			log.info("Downloading attachment from: {}", url);

			// 过滤掉头像、logo等无关链接
			if (url.contains("avatar") || url.contains("logo") || url.contains("profile")) {
				log.info("Skipping non-attachment URL: {}", url);
				return null;
			}

			// 方法1: 直接请求下载（推荐，避免页面交互）
			try {
				return downloadByDirectRequest(page, url, downloadDir, index);
			}
			catch (Exception e) {
				log.warn("Direct download failed, trying browser download: {}", e.getMessage());
			}

			// 方法2: 使用浏览器下载（降级方案）
			try {
				// 创建下载监听器
				Download download = page.waitForDownload(() -> {
					// 通过JavaScript触发下载，避免页面跳转
					String script = String.format("""
						() => {
						    // 创建临时iframe下载，避免页面跳转
						    const iframe = document.createElement('iframe');
						    iframe.style.display = 'none';
						    iframe.src = '%s';
						    document.body.appendChild(iframe);
						    
						    // 5秒后移除iframe
						    setTimeout(() => {
						        if (iframe.parentNode) {
						            iframe.parentNode.removeChild(iframe);
						        }
						    }, 5000);
						}
						""", url);
					page.evaluate(script);
				});

				// 获取建议的文件名
				String fileName = download.suggestedFilename();
				if (fileName == null || fileName.isEmpty()) {
					fileName = "attachment_" + index + getFileExtension(url);
				}

				// 保存文件
				Path filePath = downloadDir.resolve(fileName);
				download.saveAs(filePath);

				log.info("Downloaded: {}", fileName);
				return fileName;
			}
			catch (Exception e) {
				log.error("Browser download also failed: {}", e.getMessage());
				return null;
			}
		}
		catch (Exception e) {
			log.warn("Failed to download attachment from {}: {}", url, e.getMessage());
			return null;
		}
	}

	/**
	 * 直接请求下载(降级方案) - 改进版，支持Content-Type识别
	 */
	private String downloadByDirectRequest(Page page, String url, Path downloadDir, int index) throws IOException {
		// 使用Playwright的request context下载(携带当前页面的cookies)
		com.microsoft.playwright.APIResponse response = page.request().get(url);
		
		// 从响应头获取文件名
		String fileName = null;
		String contentDisposition = response.headers().get("content-disposition");
		if (contentDisposition != null) {
			Pattern pattern = Pattern.compile("filename=\"?([^\"]+)\"?");
			Matcher matcher = pattern.matcher(contentDisposition);
			if (matcher.find()) {
				try {
					fileName = java.net.URLDecoder.decode(matcher.group(1), "UTF-8");
					log.info("从响应头获取文件名: {}", fileName);
				} catch (Exception e) {
					fileName = matcher.group(1);
				}
			}
		}
		
		// 如果没有文件名，根据Content-Type生成
		if (fileName == null || fileName.isEmpty()) {
			String contentType = response.headers().get("content-type");
			String extension = getExtensionFromContentType(contentType);
			
			// 如果还是没有，从URL提取
			if (".dat".equals(extension)) {
				extension = getFileExtension(url);
			}
			
			fileName = "attachment_" + index + extension;
			log.info("生成文件名: {} (Content-Type: {})", fileName, contentType);
		}
		
		Path filePath = downloadDir.resolve(fileName);
		java.nio.file.Files.write(filePath, response.body());

		log.info("Downloaded by direct request: {}", fileName);
		return fileName;
	}

	/**
	 * 根据Content-Type获取文件扩展名
	 */
	private String getExtensionFromContentType(String contentType) {
		if (contentType == null || contentType.isEmpty()) {
			return ".dat";
		}
		
		// 移除参数部分 (e.g. "audio/mpeg; charset=utf-8" -> "audio/mpeg")
		contentType = contentType.split(";")[0].trim().toLowerCase();
		
		// 常见MIME类型映射
		Map<String, String> mimeMap = Map.ofEntries(
			Map.entry("audio/mpeg", ".mp3"),
			Map.entry("audio/mp3", ".mp3"),
			Map.entry("audio/wav", ".wav"),
			Map.entry("audio/x-wav", ".wav"),
			Map.entry("video/mp4", ".mp4"),
			Map.entry("video/mpeg", ".mpg"),
			Map.entry("application/pdf", ".pdf"),
			Map.entry("application/msword", ".doc"),
			Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"),
			Map.entry("application/vnd.ms-excel", ".xls"),
			Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),
			Map.entry("application/zip", ".zip"),
			Map.entry("application/x-rar-compressed", ".rar"),
			Map.entry("image/jpeg", ".jpg"),
			Map.entry("image/png", ".png"),
			Map.entry("image/gif", ".gif"),
			Map.entry("text/plain", ".txt"),
			Map.entry("text/csv", ".csv")
		);
		
		return mimeMap.getOrDefault(contentType, ".dat");
	}

	/**
	 * 获取文件扩展名 - 改进版，支持更多识别方式
	 */
	private String getFileExtension(String url) {
		if (url == null || url.isEmpty()) {
			return ".dat";
		}
			
		// 方法1: 从URL路径提取扩展名
		Pattern pattern = Pattern.compile("\\.(mp3|mp4|wav|pdf|doc|docx|xls|xlsx|ppt|pptx|zip|rar|7z|jpg|jpeg|png|gif|txt|csv)(?:\\?|$)", Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(url);
		if (matcher.find()) {
			return "." + matcher.group(1).toLowerCase();
		}
			
		// 方法2: 从URL参数中查找文件名
		try {
			java.net.URI uri = new java.net.URI(url);
			String query = uri.getQuery();
			if (query != null && query.contains("filename=")) {
				Pattern fileNamePattern = Pattern.compile("filename=([^&]+)");
				Matcher fileNameMatcher = fileNamePattern.matcher(query);
				if (fileNameMatcher.find()) {
					String fileName = java.net.URLDecoder.decode(fileNameMatcher.group(1), "UTF-8");
					int dotIndex = fileName.lastIndexOf('.');
					if (dotIndex > 0) {
						return fileName.substring(dotIndex);
					}
				}
			}
		} catch (Exception e) {
			log.debug("Failed to parse URL for filename: {}", e.getMessage());
		}
			
		// 方法3: 根据URL特征猜测类型
		if (url.contains("audio") || url.contains("sound") || url.contains("music")) {
			return ".mp3";
		}
		if (url.contains("video") || url.contains("movie")) {
			return ".mp4";
		}
		if (url.contains("image") || url.contains("photo") || url.contains("picture")) {
			return ".jpg";
		}
		if (url.contains("document") || url.contains("file")) {
			return ".pdf";
		}
			
		return ".dat";
	}

	/**
	 * 获取下载目录
	 */
	private Path getDownloadDirectory(String customPath, String groupId, String topicId) throws IOException {
		Path downloadDir;
		if (customPath != null && !customPath.isEmpty()) {
			downloadDir = Paths.get(customPath);
		}
		else {
			downloadDir = unifiedDirectoryManager.getRootPlanDirectory(rootPlanId)
				.resolve("zsxq_downloads")
				.resolve(groupId)
				.resolve(topicId);
		}

		unifiedDirectoryManager.ensureDirectoryExists(downloadDir);
		return downloadDir;
	}

	@Override
	public String getCurrentToolStateString() {
		return """
				知识星球爬虫工具状态:
				- 支持登录、爬取帖子、下载附件
				- Cookie已持久化保存
				- 下载文件保存在: zsxq_downloads/<group_id>/<topic_id>/
				""";
	}

	@Override
	public String getServiceGroup() {
		return "crawler-tools";
	}

	@Override
	public boolean isSelectable() {
		return true;
	}

	@Override
	public void cleanup(String planId) {
		log.info("Cleaning up zsxq_crawler for plan: {}", planId);
	}

}
