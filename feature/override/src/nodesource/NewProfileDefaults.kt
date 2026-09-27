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

/**
 * KimiNoBox: what a profile made from node sources starts with (docs/plan-a-round4.md E3). Its own
 * file is empty; the base fields, groups and rules live in the 「默认配置」 override, checked on
 * 「从节点源新建配置」 by default. It is a user override like any other: it can be edited, left
 * unchecked, or deleted.
 */
object NewProfileDefaults {
    const val DEFAULT_CONFIG_ID = "kiminobox-default-config"
    const val DEFAULT_CONFIG_NAME = "默认配置"
    const val ACL4SSR_ID = "builtin-acl4ssr-online-full"

    /** The overrides checked until the user sets others: 默认配置 alone, as it carries ACL4SSR. */
    val INITIAL_OVERRIDES = listOf(DEFAULT_CONFIG_ID)

    /** The fields a profile made from node sources used to carry in its own file. */
    private const val BASE_FIELDS =
        "mixed-port: 7890\n" +
            "mode: rule\n" +
            "log-level: info\n" +
            "ipv6: false\n"

    /** The one group and rule of 默认配置 when the built-in ACL4SSR can't be read. */
    private const val FALLBACK_RULES =
        "proxy-groups:\n" +
            "  - {name: 节点选择, type: select, include-all: true}\n" +
            "rules:\n" +
            "  - MATCH,节点选择\n"

    /**
     * The 默认配置 override: the base fields, then [acl4ssr], the groups, rule providers and rules
     * of the built-in ACL4SSR Online Full as they are when it is made. A copy, so that they can be
     * edited here; the built-in one can't be.
     */
    fun defaultConfig(acl4ssr: String?): String {
        val rules = acl4ssr?.trimEnd()?.takeIf { it.isNotEmpty() }
        return "# 新建配置时默认勾选的基础配置，可以随意修改。\n" +
            (if (rules != null) "# 代理组、规则集和规则复制自内置的 ACL4SSR Online Full。\n" else "") +
            BASE_FIELDS +
            (rules?.plus("\n") ?: FALLBACK_RULES)
    }

    /** The file of a profile made from node sources: everything in it comes from its overrides. */
    const val EMPTY_PROFILE = "# Made by KimiNoBox from node sources: all of it comes from the overrides bound to it.\n{}\n"

    /** The saved default overrides that still exist, in the saved order; deleted ones are skipped. */
    fun checked(saved: List<String>, existing: Collection<String>): List<String> = saved.distinct().filter { it in existing }

    /** Whether 默认配置 is to be made now: only the first time, and never over one that is there. */
    fun makeDefaultConfig(madeBefore: Boolean, exists: Boolean): Boolean = !madeBefore && !exists
}
