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

import com.github.yumeyucca.yumebox.data.store.MMKVPreference
import com.tencent.mmkv.MMKV

/** KimiNoBox: what 「从节点源新建配置」 keeps between uses (docs/plan-a-round4.md E3). */
class NewProfileDefaultsStore(mmkv: MMKV) : MMKVPreference(externalMmkv = mmkv) {
    /** The overrides checked by default, in order, as 「设为默认」 saved them. */
    val overrideIds by stringListFlow(NewProfileDefaults.INITIAL_OVERRIDES)

    /** Set once 默认配置 has been made, so that one the user deleted is not made again. */
    val defaultConfigMade by boolFlow(false)
}
