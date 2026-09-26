/*
 * This file is part of KimiNoBox, a modified version of YumeBox.
 *
 * YumeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * Copyright (c) 2026 KimiNoBox contributors
 *
 */

package com.github.yumeyucca.yumebox.nodesource

/** Texts of the node source screens; they move into the locale module if this goes upstream. */
object NodeText {
    const val NODE_SOURCE = "节点源"
    const val SELF_NODES = "自建节点"
    const val SECTION = "节点源"
    const val NEW_NODE_SOURCE = "新建节点源"
    const val EDIT_NODE_SOURCE = "编辑节点源"
    const val NODE_SOURCE_HINT = "填写订阅地址，保存后生成一个 YAML 覆写，内核按间隔下载订阅"
    const val TEMPLATE_HINT = "配置里的代理组需要 include-all: true 才会包含这些节点"

    const val NAME = "名称"
    const val NAME_SUPPORT = "即 provider 名称，在所有节点源之间唯一"
    const val URL = "订阅地址"
    const val INTERVAL = "更新间隔（秒）"
    const val INTERVAL_SUPPORT = "默认 86400（一天），0 表示不自动更新"
    const val PREFIX = "节点前缀"
    const val NO_PREFIX = "不加前缀"
    fun prefixSupport(effective: String) = "加在每个节点名称前，留空时用「名称 + 空格」。当前：「$effective」"

    const val NAME_REQUIRED = "请输入名称"
    const val NAME_SPACES = "名称首尾不能有空格"
    const val NAME_TAKEN = "这个名称已被另一个节点源使用"
    const val CONTROL_CHARS = "不能包含换行等控制字符"
    const val URL_INVALID = "请输入 http:// 或 https:// 开头的地址，中间不能有空格"
    const val INTERVAL_INVALID = "请输入 0 或正整数（秒）"

    const val NEW_SELF_NODES = "新建自建节点"
    const val EDIT_SELF_NODES = "编辑自建节点"
    const val SELF_NODES_HINT = "前缀加在每个节点名称前，留空则不加；名称在所有节点源之间唯一"
    const val BLANK_NODES =
        "# 在这里粘贴节点：节点列表、带 proxies: 的映射（如整份配置）或单个节点都可以。\n" +
            "# 每个节点都要有 name、type、server、port，名称不能重复。例如：\n" +
            "# - {name: \"🇸🇬 SG\", type: ss, server: 192.0.2.22, port: 8388, cipher: aes-128-gcm, password: \"密码\"}\n"
    const val PORT_INVALID = "端口必须是 1–65535 的整数"
    const val NO_NODES = "还没有节点"
    const val UNKNOWN_SHAPE = "无法识别：请粘贴节点列表、带 proxies: 的映射或单个节点"
    fun yamlInvalid(detail: String) = "YAML 格式错误：$detail"
    fun notANode(position: Int) = "第 $position 项不是节点（应为映射）"
    fun nthNode(position: Int) = "第 $position 个节点"
    fun missingKeys(label: String, keys: List<String>) = "$label：缺少 ${keys.joinToString("、")}"
    fun duplicateName(name: String) = "节点名称重复：$name"
    const val OPENED_AS_YAML = "这个节点源被手动改过，已用 YAML 编辑器打开"
    const val SAVE = "保存"
    const val SAVED = "已保存"
    fun saveFailed(reason: String?) = "保存失败：${reason ?: "未知错误"}"
}
