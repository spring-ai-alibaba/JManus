#!/usr/bin/env node

const fs = require('fs');
const path = require('path');

// Define color mappings to CSS variables
const colorMappings = {
  '#0a0a0a': 'var(--bg-primary, #0a0a0a)',
  '#1a1a1a': 'var(--bg-secondary, #1a1a1a)',
  '#2a2a2a': 'var(--bg-tertiary, #2a2a2a)',
  '#1e1e1e': 'var(--bg-card, #1e1e1e)',
  '#2d2d2d': 'var(--bg-input, #2d2d2d)',
  '#ffffff': 'var(--text-primary, #ffffff)',
  '#cccccc': 'var(--text-secondary, #cccccc)',
  '#aaaaaa': 'var(--text-tertiary, #aaaaaa)',
  'rgba(255, 255, 255, 0.2)': 'var(--border-primary, rgba(255, 255, 255, 0.2))',
  'rgba(102, 126, 234, 0.3)': 'var(--border-secondary, rgba(102, 126, 234, 0.3))',
  '#667eea': 'var(--accent-primary, #667eea)',
  '#7c9eff': 'var(--accent-secondary, #7c9eff)',
  '#a3bffa': 'var(--accent-tertiary, #a3bffa)',
  '#22c55e': 'var(--success, #22c55e)',
  '#fbbf24': 'var(--warning, #fbbf24)',
  '#ef4444': 'var(--error, #ef4444)',
  '#3b82f6': 'var(--info, #3b82f6)',
  'rgba(255, 255, 255, 0.05)': 'var(--scrollbar-track, rgba(255, 255, 255, 0.05))',
  'rgba(255, 255, 255, 0.2)': 'var(--scrollbar-thumb, rgba(255, 255, 255, 0.2))',
  'rgba(255, 255, 255, 0.3)': 'var(--scrollbar-thumb-hover, rgba(255, 255, 255, 0.3))',
  'rgba(102, 126, 234, 0.3)': 'var(--selection-bg, rgba(102, 126, 234, 0.3))'
};

// Function to process a file
function processFile(filePath) {
  if (!fs.existsSync(filePath) || !filePath.endsWith('.vue')) {
    return;
  }

  let content = fs.readFileSync(filePath, 'utf8');
  let modified = false;

  // Replace color values with CSS variables
  Object.keys(colorMappings).forEach(color => {
    const regex = new RegExp(color.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g');
    if (content.match(regex)) {
      content = content.replace(regex, colorMappings[color]);
      modified = true;
    }
  });

  // Write back if modified
  if (modified) {
    fs.writeFileSync(filePath, content, 'utf8');
    console.log(`Processed: ${filePath}`);
  }
}

// Function to recursively process directory
function processDirectory(dirPath) {
  const items = fs.readdirSync(dirPath);
  
  items.forEach(item => {
    const fullPath = path.join(dirPath, item);
    const stat = fs.statSync(fullPath);
    
    if (stat.isDirectory()) {
      processDirectory(fullPath);
    } else if (stat.isFile() && fullPath.endsWith('.vue')) {
      processFile(fullPath);
    }
  });
}

// Main execution
const componentsDir = path.join(__dirname, '../src/components');
const viewsDir = path.join(__dirname, '../src/views');

console.log('Starting color replacement process...');
processDirectory(componentsDir);
processDirectory(viewsDir);
console.log('Color replacement process completed.');