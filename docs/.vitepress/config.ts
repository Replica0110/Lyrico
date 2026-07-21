import { defineConfig } from 'vitepress'
import { GitChangelog, GitChangelogMarkdownSection } from '@nolebase/vitepress-plugin-git-changelog'

const base = '/Lyrico/'

const zhSearch = {
  provider: 'local' as const,
  options: {
    translations: {
      button: {
        buttonText: '搜索',
        buttonAriaLabel: '搜索',
      },
      modal: {
        noResultsText: '无法找到相关结果',
        resetButtonTitle: '清除查询条件',
        footer: {
          selectText: '选择',
          navigateText: '切换',
          closeText: '关闭',
        },
      },
    },
  },
}

const enSearch = {
  provider: 'local' as const,
}

const socialLinks = [
  { icon: 'github' as const, link: 'https://github.com/Replica0110/Lyrico' },
]

const zhSidebar = {
  '/guide/': [
    {
      text: '使用指南',
      items: [
        { text: '指南首页', link: '/guide/' },
        { text: '快速开始', link: '/guide/getting-started' },
        { text: '浏览音乐库', link: '/guide/browsing' },
        { text: '资料库与文件夹', link: '/guide/library' },
        { text: '单曲编辑', link: '/guide/single-song' },
        { text: '批量操作', link: '/guide/batch' },
        { text: '使用插件', link: '/guide/plugins' },
      ],
    },
    {
      text: '设置',
      items: [
        { text: '设置概览', link: '/guide/settings/' },
        { text: '外观', link: '/guide/settings/appearance' },
        { text: '搜索设置', link: '/guide/settings/search' },
        { text: '歌词设置', link: '/guide/settings/lyrics' },
        { text: '元数据处理', link: '/guide/settings/metadata' },
        { text: '备份与恢复', link: '/guide/settings/backup' },
        { text: '维护', link: '/guide/settings/maintenance' },
      ],
    },
    {
      text: '更多',
      items: [
        { text: '常见问题', link: '/guide/faq' },
      ],
    },
  ],
  '/plugins/': [
    {
      text: '使用插件',
      items: [
        { text: '插件首页', link: '/plugins/' },
        { text: '使用插件', link: '/plugins/using' },
      ],
    },
    {
      text: '开发插件',
      items: [
        { text: '从零编写插件', link: '/plugins/examples' },
        { text: '本地调试插件', link: '/plugins/debugging' },
        { text: '插件包结构', link: '/plugins/composition' },
        { text: '插件函数', link: '/plugins/plugin-functions' },
        { text: '配置与结果字段', link: '/plugins/config-metadata' },
      ],
    },
    {
      text: '参考手册',
      items: [
        { text: 'Manifest 参考', link: '/plugins/manifest' },
        { text: '宿主 API 参考', link: '/plugins/host-api' },
      ],
    },
    {
      text: '运行机制',
      items: [
        { text: '架构与生命周期', link: '/plugins/overview' },
      ],
    },
  ],
}

const enSidebar = {
  '/en-US/guide/': [
    {
      text: 'User Guide',
      items: [
        { text: 'Guide Home', link: '/en-US/guide/' },
        { text: 'Getting Started', link: '/en-US/guide/getting-started' },
        { text: 'Browsing Your Library', link: '/en-US/guide/browsing' },
        { text: 'Library & Folders', link: '/en-US/guide/library' },
        { text: 'Single Song Editing', link: '/en-US/guide/single-song' },
        { text: 'Batch Operations', link: '/en-US/guide/batch' },
        { text: 'Using Plugins', link: '/en-US/guide/plugins' },
      ],
    },
    {
      text: 'Settings',
      items: [
        { text: 'Settings Overview', link: '/en-US/guide/settings/' },
        { text: 'Appearance', link: '/en-US/guide/settings/appearance' },
        { text: 'Search Settings', link: '/en-US/guide/settings/search' },
        { text: 'Lyrics Settings', link: '/en-US/guide/settings/lyrics' },
        { text: 'Metadata Processing', link: '/en-US/guide/settings/metadata' },
        { text: 'Backup & Restore', link: '/en-US/guide/settings/backup' },
        { text: 'Maintenance', link: '/en-US/guide/settings/maintenance' },
      ],
    },
    {
      text: 'More',
      items: [
        { text: 'FAQ', link: '/en-US/guide/faq' },
      ],
    },
  ],
  '/en-US/plugins/': [
    {
      text: 'Using Plugins',
      items: [
        { text: 'Plugin Home', link: '/en-US/plugins/' },
        { text: 'Using Plugins', link: '/en-US/plugins/using' },
      ],
    },
    {
      text: 'Developing Plugins',
      items: [
        { text: 'Build a Plugin', link: '/en-US/plugins/examples' },
        { text: 'Debug Plugins Locally', link: '/en-US/plugins/debugging' },
        { text: 'Plugin Package Structure', link: '/en-US/plugins/composition' },
        { text: 'Plugin Functions', link: '/en-US/plugins/plugin-functions' },
        { text: 'Configuration and Result Fields', link: '/en-US/plugins/config-metadata' },
      ],
    },
    {
      text: 'Reference',
      items: [
        { text: 'Manifest Reference', link: '/en-US/plugins/manifest' },
        { text: 'Host API Reference', link: '/en-US/plugins/host-api' },
      ],
    },
    {
      text: 'Internals',
      items: [
        { text: 'Architecture and Lifecycle', link: '/en-US/plugins/overview' },
      ],
    },
  ],
}

export default defineConfig({
  title: 'Lyrico',
  description: 'Lyrico 使用说明与插件开发文档',
  lang: 'zh-CN',
  base,
  locales: {
    root: {
      label: '简体中文',
      lang: 'zh-CN',
      link: '/',
      title: 'Lyrico',
      description: 'Lyrico 使用说明与插件开发文档',
      themeConfig: {
        nav: [
          { text: '首页', link: '/' },
          { text: '使用指南', link: '/guide/' },
          { text: '插件', link: '/plugins/' },
        ],
        returnToTopLabel: '回到顶部',
        sidebarMenuLabel: '菜单',
        darkModeSwitchLabel: '主题',
        lightModeSwitchTitle: '切换到浅色模式',
        darkModeSwitchTitle: '切换到深色模式',
        search: zhSearch,
        sidebar: zhSidebar,
        socialLinks,
        outline: {
          level: [2, 3],
          label: '本页目录',
        },
        docFooter: {
          prev: '上一页',
          next: '下一页',
        },
        lastUpdated: {
          text: '最后更新',
        },
      },
    },
    'en-US': {
      label: 'English',
      lang: 'en-US',
      link: '/en-US/',
      title: 'Lyrico',
      description: 'Lyrico user guide and plugin development docs',
      themeConfig: {
        nav: [
          { text: 'Home', link: '/en-US/' },
          { text: 'Guide', link: '/en-US/guide/' },
          { text: 'Plugins', link: '/en-US/plugins/' },
        ],
        returnToTopLabel: 'Return to top',
        sidebarMenuLabel: 'Menu',
        darkModeSwitchLabel: 'Theme',
        lightModeSwitchTitle: 'Switch to light mode',
        darkModeSwitchTitle: 'Switch to dark mode',
        search: enSearch,
        sidebar: enSidebar,
        socialLinks,
        outline: {
          level: [2, 3],
          label: 'On this page',
        },
        docFooter: {
          prev: 'Previous page',
          next: 'Next page',
        },
        lastUpdated: {
          text: 'Last updated',
        },
      },
    },
  },

  head: [
    ['link', { rel: 'icon', href: `/Lyrico/logo.svg`, type: 'image/svg+xml' }],
  ],

  vite: {
    plugins: [
      GitChangelog({
        repoURL: () => 'https://github.com/Replica0110/Lyrico',
      }),
      GitChangelogMarkdownSection(),
    ],
  },

  themeConfig: {
    logo: `/logo.svg`,
    nav: [
      { text: '首页', link: '/' },
      { text: '使用指南', link: '/guide/' },
      { text: '插件', link: '/plugins/' },
      { text: 'English', link: '/en-US/' },
    ],
    search: zhSearch,
    sidebar: zhSidebar,
    socialLinks,
    outline: {
      level: [2, 3],
      label: '本页目录',
    },
    docFooter: {
      prev: '上一页',
      next: '下一页',
    },
    lastUpdated: {
      text: '最后更新',
    },
  },
})
