package com.vita.healthtracker.domain

import com.vita.healthtracker.data.local.entity.HabitCheckIn
import com.vita.healthtracker.data.local.entity.HabitDefinition
import java.time.LocalDate

private const val HabitBadgeSystemId = "habit_checkin"
private const val HabitBadgeSystemName = "习惯打卡"
private const val FitnessBadgeSystemId = "fitness_training"
private const val FitnessBadgeSystemName = "健身运动"

enum class HabitBadgeStyle {
    // —— 习惯打卡（Good Omens DNA）——
    Crowley,
    Aziraphale,
    Doctor,
    Stage,
    Legendary,
    // —— 健身运动（实验室多元宇宙）——
    LabAcid,        // 启动 / 一周连击
    PortalPlasma,   // 节奏 / 万步穿越 / 维度入口
    ReactorHazard,  // 力量 / 爬坡 / 燃烧
    RiftBreak,      // 耐力 / 蓝色裂隙 II
    LabApex,        // 巅峰
}

data class HabitBadge(
    val id: String,
    val name: String,
    val thresholdDays: Int,
    val tag: String,
    val description: String,
    val style: HabitBadgeStyle,
    val systemId: String = HabitBadgeSystemId,
    val systemName: String = HabitBadgeSystemName,
)

data class BadgeSystem(
    val id: String,
    val name: String,
    val badges: List<HabitBadge>,
)

object HabitBadgeCatalog {
    const val systemId = HabitBadgeSystemId
    const val systemName = HabitBadgeSystemName

    val badges = listOf(
        HabitBadge("red_mirror", "红镜启动", 1, "1 天", "第一天完成打卡。别想太多，先把今天练完。", HabitBadgeStyle.Crowley),
        HabitBadge("old_book_halo", "旧书光环", 3, "3 天", "连续三天，习惯开始有了书签。准时出现就是胜利。", HabitBadgeStyle.Aziraphale),
        HabitBadge("time_runner", "时间跑者", 7, "1 周", "一周达成。你已经不只是偶尔运动。", HabitBadgeStyle.Doctor),
        HabitBadge("double_rehearsal", "双人排练", 14, "14 天", "两周后进入排练感：能复盘、能接上、能再来一条。", HabitBadgeStyle.Stage),
        HabitBadge("serpent_wings", "蛇与圣翼", 30, "30 天", "一个月形成习惯，冲劲和耐心开始握手。", HabitBadgeStyle.Legendary),
        HabitBadge("blue_rift", "蓝色裂隙", 90, "90 天", "三个月打破最危险的中断期，像改写了时间线。", HabitBadgeStyle.Doctor),
        HabitBadge("night_guard", "夜路守护", 180, "180 天", "半年坚持不是热血，是能在夜路里继续开下去的秩序。", HabitBadgeStyle.Crowley),
        HabitBadge("miracle_crown", "年度奇迹王冠", 365, "365 天", "一年坚持的最终章，给自己一个真正稀有的成就感。", HabitBadgeStyle.Legendary),
    )

    val fitnessBadges = listOf(
        // ───── 01 启动 ─────
        HabitBadge(
            id = "fit_first_sweat",
            name = "第一滴汗",
            thresholdDays = 1,
            tag = "完成 1 次运动",
            description = "门槛最低的一枚——把今天的运动记录写下，烧瓶里就有了第一滴汗。",
            style = HabitBadgeStyle.LabAcid,
            systemId = FitnessBadgeSystemId,
            systemName = FitnessBadgeSystemName,
        ),
        HabitBadge(
            id = "fit_portal_entry",
            name = "维度入口",
            thresholdDays = 1,
            tag = "首次峰值心率 ≥ 150",
            description = "心率第一次撞穿 150。门开了——你看见了平时不会出现的自己。",
            style = HabitBadgeStyle.PortalPlasma,
            systemId = FitnessBadgeSystemId,
            systemName = FitnessBadgeSystemName,
        ),

        // ───── 02 节奏 ─────
        HabitBadge(
            id = "fit_rhythm",
            name = "训练节奏",
            thresholdDays = 3,
            tag = "7 天内 3 次训练",
            description = "一台心跳计 + 节拍器——你的训练已经能被预测下一次出现的时间。",
            style = HabitBadgeStyle.PortalPlasma,
            systemId = FitnessBadgeSystemId,
            systemName = FitnessBadgeSystemName,
        ),
        HabitBadge(
            id = "fit_week_combo",
            name = "一周连击",
            thresholdDays = 5,
            tag = "自然周内 5 天有训练",
            description = "七格能量条——你这周不只是「有动」，是「持续在动」。",
            style = HabitBadgeStyle.LabAcid,
            systemId = FitnessBadgeSystemId,
            systemName = FitnessBadgeSystemName,
        ),

        // ───── 03 力量 ─────
        HabitBadge(
            id = "fit_force_atom",
            name = "力量分子",
            thresholdDays = 10,
            tag = "累计 10 次力量训练",
            description = "原子轨道里嵌着哑铃——力量不是堆砝码，是稳定地转起来。",
            style = HabitBadgeStyle.ReactorHazard,
            systemId = FitnessBadgeSystemId,
            systemName = FitnessBadgeSystemName,
        ),
        HabitBadge(
            id = "fit_beast_subject",
            name = "怪兽实验体",
            thresholdDays = 1,
            tag = "单次力量训练时长 PR",
            description = "试管里那只小生物今天举起了之前举不起来的重量——它和你一起。",
            style = HabitBadgeStyle.ReactorHazard,
            systemId = FitnessBadgeSystemId,
            systemName = FitnessBadgeSystemName,
        ),

        // ───── 04 耐力 ─────
        HabitBadge(
            id = "fit_core_battery",
            name = "续航之核",
            thresholdDays = 1,
            tag = "单次有氧 ≥ 60 分钟",
            description = "反应堆芯亮起来——你的身体证明它能撑过一小时的稳定输出。",
            style = HabitBadgeStyle.RiftBreak,
            systemId = FitnessBadgeSystemId,
            systemName = FitnessBadgeSystemName,
        ),
        HabitBadge(
            id = "fit_step_warp",
            name = "万步穿越",
            thresholdDays = 1,
            tag = "单日步数 ≥ 20000",
            description = "脚印穿过一个小小的传送门——你的腿今天替你试过了平行宇宙。",
            style = HabitBadgeStyle.PortalPlasma,
            systemId = FitnessBadgeSystemId,
            systemName = FitnessBadgeSystemName,
        ),

        // ───── 05 突破 ─────
        HabitBadge(
            id = "fit_climb_rift",
            name = "坡度裂隙",
            thresholdDays = 5,
            tag = "累计 5 次爬坡 / 海拔训练",
            description = "山尖上劈开一道闪电——爬坡训练第五次了，海拔曲线已经会自己生长。",
            style = HabitBadgeStyle.ReactorHazard,
            systemId = FitnessBadgeSystemId,
            systemName = FitnessBadgeSystemName,
        ),
        HabitBadge(
            id = "fit_burn_lab",
            name = "燃烧实验",
            thresholdDays = 1,
            tag = "累计主动消耗 ≥ 10000 kcal",
            description = "本生灯下烧瓶咕嘟咕嘟——一万千卡，已经够把一个小镇的早餐做出来。",
            style = HabitBadgeStyle.ReactorHazard,
            systemId = FitnessBadgeSystemId,
            systemName = FitnessBadgeSystemName,
        ),
        HabitBadge(
            id = "fit_blue_rift",
            name = "蓝色裂隙 II",
            thresholdDays = 1,
            tag = "配速 PR（同距离对比）",
            description = "一道大裂隙被你劈开——这次的配速比你上一版自己更快。",
            style = HabitBadgeStyle.RiftBreak,
            systemId = FitnessBadgeSystemId,
            systemName = FitnessBadgeSystemName,
        ),

        // ───── 06 巅峰 ─────
        HabitBadge(
            id = "fit_lab_king",
            name = "实验室之王",
            thresholdDays = 365,
            tag = "训练记录连续 365 天",
            description = "把所有徽章的元素叠在一顶冠上——一年没断的训练，本身就是稀有材料。",
            style = HabitBadgeStyle.LabApex,
            systemId = FitnessBadgeSystemId,
            systemName = FitnessBadgeSystemName,
        ),
    )

    val systems = listOf(
        BadgeSystem(HabitBadgeSystemId, "习惯打卡徽章", badges),
        BadgeSystem(FitnessBadgeSystemId, "健身运动徽章", fitnessBadges),
    )

    fun earnedIds(longestStreakDays: Int): Set<String> =
        badges.filter { longestStreakDays >= it.thresholdDays }.mapTo(mutableSetOf()) { it.id }

    fun earnedBadgeTokens(habits: List<HabitDefinition>, checkIns: List<HabitCheckIn>): Set<String> =
        habits.flatMap { habit ->
            val longest = longestStreak(checkIns, habit.id)
            badges
                .filter { longest >= it.thresholdDays }
                .map { badge -> token(habit.id, badge.id) }
        }.toSet()

    fun earnedCountsByBadge(tokens: Set<String>): Map<String, Int> =
        tokens.map { badgeIdFromToken(it) }.groupingBy { it }.eachCount()

    fun earnedCountsByBadge(habits: List<HabitDefinition>, checkIns: List<HabitCheckIn>): Map<String, Int> =
        earnedCountsByBadge(earnedBadgeTokens(habits, checkIns))

    fun badgeIdFromToken(token: String): String = token.substringAfter(':', token)

    fun longestStreak(checkIns: List<HabitCheckIn>, habitId: String? = null): Int {
        val doneDates = doneDates(checkIns, habitId).sorted()
        if (doneDates.isEmpty()) return 0

        var longest = 1
        var current = 1
        for (index in 1 until doneDates.size) {
            current = if (doneDates[index - 1].plusDays(1) == doneDates[index]) {
                current + 1
            } else {
                1
            }
            if (current > longest) longest = current
        }
        return longest
    }

    fun currentStreak(
        checkIns: List<HabitCheckIn>,
        today: LocalDate = LocalDate.now(),
        habitId: String? = null,
    ): Int {
        val doneDates = doneDates(checkIns, habitId).toSet()
        var current = today
        var count = 0
        while (current in doneDates) {
            count += 1
            current = current.minusDays(1)
        }
        return count
    }

    private fun token(habitId: String, badgeId: String): String = "$habitId:$badgeId"

    private fun doneDates(checkIns: List<HabitCheckIn>, habitId: String?): List<LocalDate> =
        checkIns
            .asSequence()
            .filter { it.status == HabitCheckIn.StatusDone }
            .filter { habitId == null || it.habitId == habitId }
            .mapNotNull { runCatching { LocalDate.parse(it.date) }.getOrNull() }
            .distinct()
            .toList()
}
