# KimiNoBox

KimiNoBox 是 [YumeBox](https://github.com/YumeYucca/YumeBox) 的修改版，一个基于 [mihomo](https://github.com/MetaCubeX/mihomo) 的 Android 客户端。修改部分由 AI 开发，仅供学习和研究使用。

它不是 YumeBox 的官方版本。遇到问题请在本仓库提交 [Issue](https://github.com/andya1lan/KimiNoBox/issues)，不要反馈给 YumeBox。

## 与 YumeBox 的不同

- **节点源**：机场订阅或自建节点列表是一个覆写，可以绑定到任意多个配置。添加订阅时 App 下载一次，之后由内核按更新间隔自动更新。自建节点直接粘贴 YAML。
- **从节点源新建配置**：勾选节点源和覆写即可。默认勾选「默认配置」，内容是基础设置加 ACL4SSR Online Full 的代理组和规则，可以自己改。
- **配置管理**：在配置卡片上管理节点源和覆写，拖动排序；代理页按节点源分组显示节点；新版本配置启动失败时，自动恢复上一个能启动的版本；可以查看导入的原始配置和最终生效的配置。
- **细节**：首页显示出口 IP，点一下节点重新测延迟；代码编辑器有「自动换行」开关；修复了 GeoX、Sub-Store 下载时闪退，以及日志页、外部资源更新的一些问题。
- **精简**：固定使用内置的 mihomo 内核，只保留 VPN 运行模式；隐藏了内核下载与切换、Tun / eBPF、实验室和 age 密钥等选项。

## 下载与构建

- **下载**：[Releases](https://github.com/andya1lan/KimiNoBox/releases)。Release 将只提供 arm64-v8a、内置 Geo 数据的 APK。
- **自行构建**：参照 YumeBox 的[构建文档](https://yumebox.yumeyuka.moe/guide/build)，有三处不同：
  1. 用本仓库的 `Moe` 分支：`git clone -b Moe https://github.com/andya1lan/KimiNoBox.git`
  2. 同步内核用 `python scripts/sync_kernel.py pinned`，它拉取 `kernel.properties` 里锁定的 mihomo 版本。
  3. 自己构建的 APK 和 Release 签名不同，安装前要先卸载 Release 版。

## 许可证

KimiNoBox 和 YumeBox 一样使用 [GNU AGPL v3](https://github.com/andya1lan/KimiNoBox/blob/Moe/LICENSE) 许可证。每个版本的源码就是同名的 tag。

[English](README.md)
