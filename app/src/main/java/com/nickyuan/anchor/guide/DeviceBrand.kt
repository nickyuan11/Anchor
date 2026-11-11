package com.nickyuan.anchor.guide

/**
 * 手机品牌。不同品牌的系统，"自启动""省电"这些设置藏在不同的地方，所以保活引导要按品牌区分
 * （design.md 第 7 节"机型识别"）。
 */
enum class DeviceBrand(val label: String) {
    XIAOMI("小米 / 红米（澎湃 OS、MIUI）"),
    HUAWEI("华为 / 荣耀（鸿蒙、EMUI）"),
    OTHER("其他品牌"),
    ;

    companion object {
        /**
         * @param manufacturer 系统提供的厂商名（Build.MANUFACTURER），例如 "Xiaomi"、"HUAWEI"
         * @param brand 系统提供的品牌名（Build.BRAND），例如 "Redmi"、"HONOR"
         */
        fun detect(manufacturer: String?, brand: String?): DeviceBrand {
            val names = listOfNotNull(manufacturer, brand).map { it.lowercase() }
            return when {
                names.any { it in XIAOMI_NAMES } -> XIAOMI
                names.any { it in HUAWEI_NAMES } -> HUAWEI
                else -> OTHER
            }
        }

        private val XIAOMI_NAMES = setOf("xiaomi", "redmi", "poco")
        private val HUAWEI_NAMES = setOf("huawei", "honor")
    }
}
