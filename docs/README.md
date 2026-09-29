# KimiNoBox

KimiNoBox is a modified version of [YumeBox](https://github.com/YumeYucca/YumeBox), an Android client based on [mihomo](https://github.com/MetaCubeX/mihomo). It is not an official YumeBox build; please don't report its problems to YumeBox.

It keeps nodes and configs apart:

- **Node sources**: an airport subscription or a list of self-hosted nodes is an override that any number of profiles can share. The app downloads a subscription once when you add it, and the core keeps it up to date.
- **New profile from node sources**: pick node sources and overrides. By default a profile gets 「默认配置」, the base settings plus the ACL4SSR Online Full groups and rules, which you can edit.

Download it from [Releases](https://github.com/andya1lan/KimiNoBox/releases). It runs on arm64-v8a only.

KimiNoBox is licensed under the [GNU AGPL v3](../LICENSE), like YumeBox. The source of each release is the tag with the same name.

[简体中文](README_ZH_HANS.md)
