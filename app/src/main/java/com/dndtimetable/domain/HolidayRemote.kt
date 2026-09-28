package com.dndtimetable.domain

import org.json.JSONObject
import java.time.LocalDate

/**
 * 联网节假日表（自托管 JSON：GitHub raw / Gitee 等任意静态托管即可，无需服务器）。
 * schema（样例见仓库 `data/holidays.json`）：
 * ```json
 * {"years":{"2027":{
 *   "holidays":[{"name":"元旦","start":"2027-01-01","end":"2027-01-01"}],
 *   "makeups":[{"date":"2027-01-09","name":"元旦调休上课"}]}}}
 * ```
 * - `holidays`：放假区间（含首尾，即官方通知放假调休后的完整假期）；
 * - `makeups`：调休补课（上班）日——只有「哪天补班」：`source`（被补日/结束日期）**可选**，
 *   缺省 = null，App 显示在「自添加日期」区并提醒用户设置结束日期，设好后按 source 那天的课表上课。
 * 拉取/解析失败 → 返回空表，调用方不覆盖，回退 [LegalHolidays] 内置表。
 */
object HolidayRemote {
    /**
     * 托管地址（raw 直链，HTTPS）。**留空 = 不联网，仅用内置表。**
     * 仓库 data/holidays.json 更新后此链接自动生效，无需发版。
     */
    const val SOURCE_URL = "https://raw.githubusercontent.com/ZhYue314/dndtimetable/main/data/holidays.json"

    /** jsDelivr 镜像（同一仓库文件，raw 不通时兜底）；raw 与 GitHub Pages 域名在国内时常抖。 */
    private const val MIRROR_JSDLV = "https://cdn.jsdelivr.net/gh/ZhYue314/dndtimetable@main/data/holidays.json"
    private const val MIRROR_FASTLY = "https://fastly.jsdelivr.net/gh/ZhYue314/dndtimetable@main/data/holidays.json"

    /**
     * 按序尝试的拉取源：主源 + 镜像；**[SOURCE_URL] 留空 = 整个功能关掉（不联网）**，
     * 镜像只在主源启用时才跟上——否则"改一个常量关掉联网"会被镜像绕过。
     */
    fun sourceUrls(): List<String> =
        if (SOURCE_URL.isBlank()) emptyList() else listOf(SOURCE_URL, MIRROR_JSDLV, MIRROR_FASTLY)

    /** 本地缓存文件名（filesDir 下；AppGraph 启动时装入，进程重启/广播触发播种也能用上）。 */
    const val CACHE_FILE = "holidays.json"

    /** 年份 → (放假区间, 调休补课)。解析失败/空内容返回空表。 */
    fun parse(json: String): Map<Int, Pair<List<LegalHolidays.Range>, List<LegalHolidays.Makeup>>> = runCatching {
        val years = JSONObject(json).getJSONObject("years")
        val out = HashMap<Int, Pair<List<LegalHolidays.Range>, List<LegalHolidays.Makeup>>>()
        for (key in years.keys()) {
            val y = key.toIntOrNull() ?: continue
            val node = years.getJSONObject(key)
            val holidays = ArrayList<LegalHolidays.Range>()
            val arrH = node.optJSONArray("holidays")
            for (i in 0 until (arrH?.length() ?: 0)) {
                val o = arrH!!.getJSONObject(i)
                val start = parseDate(o.optString("start")) ?: continue
                val end = parseDate(o.optString("end")) ?: continue
                if (end < start) continue
                holidays.add(LegalHolidays.Range(o.optString("name"), start, end))
            }
            val makeups = ArrayList<LegalHolidays.Makeup>()
            val arrM = node.optJSONArray("makeups")
            for (i in 0 until (arrM?.length() ?: 0)) {
                val o = arrM!!.getJSONObject(i)
                val date = parseDate(o.optString("date")) ?: continue
                // source 缺省 = null（待用户设置结束日期）
                val source = parseDate(o.optString("source"))
                makeups.add(LegalHolidays.Makeup(date, source, o.optString("name").ifBlank { "调休补课" }))
            }
            if (holidays.isEmpty() && makeups.isEmpty()) continue
            out[y] = holidays to makeups
        }
        out
    }.getOrElse { emptyMap() }

    private fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s) }.getOrNull()
}
