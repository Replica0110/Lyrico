import { defineConfig } from 'vitepress'
import { GitChangelog, GitChangelogMarkdownSection } from '@nolebase/vitepress-plugin-git-changelog'

export default defineConfig({
  title: 'Lyrico 插件指南',
  description: '为 Lyrico 提供在线元数据搜索能力',
  lang: 'zh-CN',

  head: [
    ['link', { rel: 'icon', href: '/logo.svg', type: 'image/svg+xml' }],
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
    logo: '/logo.svg',
    nav: [
      { text: '首页', link: '/' },
      { text: '插件文档', link: '/plugins/' }
    ],

    sidebar: {
      '/plugins/': [
        {
          text: '插件系统',
          items: [
            { text: '概述', link: '/plugins/overview' },
            { text: '插件组成', link: '/plugins/composition' },
            { text: '完整示例', link: '/plugins/examples' }
          ]
        },
        {
          text: '参考手册',
          items: [
            { text: 'Manifest 字段', link: '/plugins/manifest' },
            { text: '宿主 API', link: '/plugins/host-api' },
            { text: '插件函数', link: '/plugins/plugin-functions' },
            { text: '配置与元数据', link: '/plugins/config-metadata' }
          ]
        }
      ]
    },

    socialLinks: [
      { icon: 'github', link: 'https://github.com/Replica0110/Lyrico' }
    ],

    outline: {
      level: [2, 3],
      label: '本页目录'
    },

    docFooter: {
      prev: '上一页',
      next: '下一页'
    },

    lastUpdated: {
      text: '最后更新'
    }
  }
})
