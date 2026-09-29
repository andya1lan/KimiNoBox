# KimiNoBox

KimiNoBox is a modified version of [YumeBox](https://github.com/YumeYucca/YumeBox), an Android client based on [mihomo](https://github.com/MetaCubeX/mihomo). The changes are developed by AI, and it is for learning and research only.

It is not an official YumeBox build. Please report problems as an [issue](https://github.com/andya1lan/KimiNoBox/issues) here, not to YumeBox.

## Differences from YumeBox

- **Node sources**: an airport subscription or a list of self-hosted nodes is an override that any number of profiles can share. The app downloads a subscription once when you add it, and the core keeps it up to date at its update interval. Self-hosted nodes are pasted as YAML.
- **New profile from node sources**: pick node sources and overrides. By default a profile gets 「默认配置」, the base settings plus the ACL4SSR Online Full groups and rules, which you can edit.
- **Profile management**: manage a profile's node sources and overrides on its card and sort them by dragging. The proxy page shows a group's nodes in sections by node source. When a new version of a config fails to start, the last one that started comes back. You can view the imported and the final config of a profile.
- **Smaller things**: the home page shows the egress IP, and a tap on the node tests its delay again. Every code editor has a soft wrap toggle. Crashes in GeoX and Sub-Store downloads and some problems in the log page and provider updates are fixed.
- **Trimmed**: the core is always the bundled mihomo, and only the VPN run mode is left. The options to download and switch cores, Tun / eBPF, the Lab and age keys are hidden.

## Download and build

- **Download** from [Releases](https://github.com/andya1lan/KimiNoBox/releases). Releases will only have the arm64-v8a APK with bundled Geo data.
- **Build it yourself** with YumeBox's [build guide](https://yumebox.yumeyuka.moe/en/guide/build), with three differences:
  1. Use this repository's `Moe` branch: `git clone -b Moe https://github.com/andya1lan/KimiNoBox.git`
  2. Sync the core with `python scripts/sync_kernel.py pinned`, which fetches the mihomo version locked in `kernel.properties`.
  3. Your APK is signed with another key than a release, so uninstall the release before you install it.

## License

KimiNoBox is licensed under the [GNU AGPL v3](https://github.com/andya1lan/KimiNoBox/blob/Moe/LICENSE), like YumeBox. The source of each release is the tag with the same name.

[简体中文](README_ZH_HANS.md)
