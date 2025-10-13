const fs = require('fs');
const path = require('path');

// 定义颜色映射关系
const colorMap = {
  // 从 dark.css 映射
  '#0a0a0a': 'var(--bg-primary)',
  '#1a1a1a': 'var(--bg-secondary)',
  '#2a2a2a': 'var(--bg-tertiary)',
  '#1e1e1e': 'var(--bg-card)',
  '#2d2d2d': 'var(--bg-input)',
  '#ffffff': 'var(--text-primary)',
  '#cccccc': 'var(--text-secondary)',
  '#aaaaaa': 'var(--text-tertiary)',
  'rgba(255, 255, 255, 0.2)': 'var(--border-primary)',
  'rgba(102, 126, 234, 0.3)': 'var(--border-secondary)',
  '#667eea': 'var(--accent-primary)',
  '#7c9eff': 'var(--accent-secondary)',
  '#a3bffa': 'var(--accent-tertiary)',
  '#22c55e': 'var(--success)',
  '#fbbf24': 'var(--warning)',
  '#ef4444': 'var(--error)',
  '#3b82f6': 'var(--info)',
  'rgba(255, 255, 255, 0.05)': 'var(--scrollbar-track)',
  'rgba(255, 255, 255, 0.2)': 'var(--scrollbar-thumb)',
  'rgba(255, 255, 255, 0.3)': 'var(--scrollbar-thumb-hover)',
  'rgba(102, 126, 234, 0.3)': 'var(--selection-bg)',
  
  // 从 light.css 映射 (部分可能与 dark.css 重复)
  '#fafafa': 'var(--bg-secondary)',
  '#f5f5f5': 'var(--bg-tertiary)',
  '#1a1a1a': 'var(--text-primary)',
  '#4d4d4d': 'var(--text-secondary)',
  '#737373': 'var(--text-tertiary)',
  '#0f0f0f': 'var(--text-heading)',
  '#267ae9': 'var(--text-link)',
  '#e0e0e0': 'var(--border-primary)',
  '#cccccc': 'var(--border-secondary)',
  '#5a6b8c': 'var(--accent-primary)',
  '#7a8ba8': 'var(--accent-secondary)',
  '#9aa9c0': 'var(--accent-tertiary)',
  '#2e8b57': 'var(--success)',
  '#d4a017': 'var(--warning)',
  '#c23934': 'var(--error)',
  '#3a86a8': 'var(--info)',
  'rgba(240, 240, 240, 1)': 'var(--scrollbar-track)',
  'rgba(200, 200, 200, 0.6)': 'var(--scrollbar-thumb)',
  'rgba(150, 150, 150, 0.8)': 'var(--scrollbar-thumb-hover)',
  'rgba(150, 150, 150, 0.25)': 'var(--selection-bg)',
  
  // 特殊处理的透明度值
  'rgba(255, 255, 255, 0.6)': 'var(--text-secondary)',
  'rgba(255, 255, 255, 0.9)': 'var(--text-primary)',
  'rgba(255, 255, 255, 0.8)': 'var(--text-secondary)',
  'rgba(255, 255, 255, 0.7)': 'var(--text-tertiary)',
  'rgba(255, 255, 255, 0.4)': 'var(--text-tertiary)',
  'rgba(255, 255, 255, 0.3)': 'var(--text-tertiary)',
  'rgba(255, 255, 255, 0.1)': 'var(--border-primary)',
  'rgba(255, 255, 255, 0.05)': 'var(--bg-secondary)',
  'rgba(255, 255, 255, 0.03)': 'var(--bg-secondary)',
  'rgba(102, 126, 234, 0.2)': 'var(--accent-primary)',
  'rgba(102, 126, 234, 0.1)': 'var(--accent-primary)',
  'rgba(102, 126, 234, 0.15)': 'var(--accent-primary)',
  'rgba(234, 102, 102, 0.1)': 'var(--error)',
  'rgba(234, 102, 102, 0.2)': 'var(--error)',
  'rgba(234, 102, 102, 0.3)': 'var(--error)',
};

// 处理单个文件
function processFile(filePath) {
  console.log(`Processing file: ${filePath}`);
  
  // 读取文件内容
  let content = fs.readFileSync(filePath, 'utf8');
  
  // 替换颜色值
  Object.entries(colorMap).forEach(([color, variable]) => {
    // 创建正则表达式，匹配颜色值（包括引号内的和不带引号的）
    const regex = new RegExp(`(?<![-a-zA-Z0-9])${color.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}(?![-a-zA-Z0-9])`, 'g');
    content = content.replace(regex, variable);
  });
  
  // 写入修改后的内容
  fs.writeFileSync(filePath, content, 'utf8');
  console.log(`Processed: ${filePath}`);
}

// 获取所有配置文件路径
function getConfigFiles() {
  const configDir = path.join(__dirname, '..', 'src', 'views', 'configs');
  const files = [];
  
  function traverseDir(dir) {
    const items = fs.readdirSync(dir);
    items.forEach(item => {
      const fullPath = path.join(dir, item);
      const stat = fs.statSync(fullPath);
      
      if (stat.isDirectory()) {
        traverseDir(fullPath);
      } else if (stat.isFile() && path.extname(fullPath) === '.vue') {
        files.push(fullPath);
      }
    });
  }
  
  traverseDir(configDir);
  return files;
}

// 主函数
function main() {
  console.log('Starting color replacement...');
  
  const configFiles = getConfigFiles();
  
  configFiles.forEach(file => {
    processFile(file);
  });
  
  console.log('Color replacement completed.');
}

main();