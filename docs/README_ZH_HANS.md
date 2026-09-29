# KimiNoBox

KimiNoBox 是 [YumeBox](https://github.com/YumeYucca/YumeBox) 的修改版，一个基于 [mihomo](https://github.com/MetaCubeX/mihomo) 的 Android 客户端。它不是 YumeBox 的官方版本，遇到问题请不要反馈给 YumeBox。

它把节点和配置分开管理：

- **节点源**：机场订阅或自建节点列表是一个覆写，可以绑定到任意多个配置。添加订阅时 App 下载一次，之后由内核自动更新。
- **从节点源新建配置**：勾选节点源和覆写即可。默认勾选「默认配置」，内容是基础设置加 ACL4SSR Online Full 的代理组和规则，可以自己改。

在 [Releases](https://github.com/andya1lan/KimiNoBox/releases) 下载。只支持 arm64-v8a。

KimiNoBox 和 YumeBox 一样使用 [GNU AGPL v3](../LICENSE) 许可证。每个版本的源码就是同名的 tag。

[English](README.md)
