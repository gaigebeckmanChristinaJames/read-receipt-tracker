package dev.ujhhgtg.wekit.features.items.beautify.home_screen_panel

import androidx.annotation.StringRes
import com.composables.icons.materialsymbols.MaterialSymbols
import com.composables.icons.materialsymbols.outlined.Account_circle
import com.composables.icons.materialsymbols.outlined.Calendar_month
import com.composables.icons.materialsymbols.outlined.Clock
import com.composables.icons.materialsymbols.outlined.Cloud
import com.composables.icons.materialsymbols.outlined.Edit_note
import com.composables.icons.materialsymbols.outlined.Format_quote
import com.composables.icons.materialsymbols.outlined.Image
import com.composables.icons.materialsymbols.outlined.List_alt
import com.composables.icons.materialsymbols.outlined.Schedule
import com.composables.icons.materialsymbols.outlined.Wallet
import com.composables.icons.materialsymbols.outlined.Directions_run
import androidx.compose.ui.graphics.vector.ImageVector
import dev.ujhhgtg.wekit.R

/**
 * 负一屏所有可用组件的枚举定义
 */
internal enum class HomeSidePanelWidget(
    @StringRes val labelRes: Int,
    @StringRes val descriptionRes: Int,
    val icon: ImageVector,
    val supportsTintColor: Boolean = true,
    val supportsCustomImage: Boolean = false,
    val supportsCustomText: Boolean = false,
) {
    PROFILE_HEADER(
        labelRes = R.string.home_side_panel_widget_profile,
        descriptionRes = R.string.home_side_panel_widget_profile_desc,
        icon = MaterialSymbols.Outlined.Account_circle,
        supportsTintColor = false,
    ),
    DATE_TIME(
        labelRes = R.string.home_side_panel_widget_datetime,
        descriptionRes = R.string.home_side_panel_widget_datetime_desc,
        icon = MaterialSymbols.Outlined.Clock,
    ),
    WEATHER(
        labelRes = R.string.home_side_panel_widget_weather,
        descriptionRes = R.string.home_side_panel_widget_weather_desc,
        icon = MaterialSymbols.Outlined.Cloud,
    ),
    WALLET(
        labelRes = R.string.home_side_panel_widget_wallet,
        descriptionRes = R.string.home_side_panel_widget_wallet_desc,
        icon = MaterialSymbols.Outlined.Wallet,
    ),
    SHORTCUTS(
        labelRes = R.string.home_side_panel_widget_shortcuts,
        descriptionRes = R.string.home_side_panel_widget_shortcuts_desc,
        icon = MaterialSymbols.Outlined.List_alt,
        supportsTintColor = false,
    ),
    FUNCTION_LIST(
        labelRes = R.string.home_side_panel_widget_functions,
        descriptionRes = R.string.home_side_panel_widget_functions_desc,
        icon = MaterialSymbols.Outlined.List_alt,
        supportsTintColor = false,
    ),
    HITOKOTO(
        labelRes = R.string.home_side_panel_widget_hitokoto,
        descriptionRes = R.string.home_side_panel_widget_hitokoto_desc,
        icon = MaterialSymbols.Outlined.Format_quote,
    ),
    CUSTOM_IMAGE(
        labelRes = R.string.home_side_panel_widget_custom_image,
        descriptionRes = R.string.home_side_panel_widget_custom_image_desc,
        icon = MaterialSymbols.Outlined.Photo_library,
        supportsTintColor = false,
        supportsCustomImage = true,
    ),
    NOTE(
        labelRes = R.string.home_side_panel_widget_note,
        descriptionRes = R.string.home_side_panel_widget_note_desc,
        icon = MaterialSymbols.Outlined.Edit_note,
        supportsCustomText = true,
    ),
    COUNTDOWN(
        labelRes = R.string.home_side_panel_widget_countdown,
        descriptionRes = R.string.home_side_panel_widget_countdown_desc,
        icon = MaterialSymbols.Outlined.Schedule,
        supportsCustomText = true,
    ),
    CALENDAR(
        labelRes = R.string.home_side_panel_widget_calendar,
        descriptionRes = R.string.home_side_panel_widget_calendar_desc,
        icon = MaterialSymbols.Outlined.Calendar_month,
    ),
    STEPS(
        labelRes = R.string.home_side_panel_widget_steps,
        descriptionRes = R.string.home_side_panel_widget_steps_desc,
        icon = MaterialSymbols.Outlined.Directions_run,
    ),
    ;

    companion object {
        /**
         * 默认启用的组件及排序
         */
        val DEFAULT_ENABLED_ORDER: List<HomeSidePanelWidget> = listOf(
            PROFILE_HEADER,
            DATE_TIME,
            WEATHER,
            WALLET,
            SHORTCUTS,
            FUNCTION_LIST,
            HITOKOTO,
        )

        /**
         * 所有组件的默认配置
         */
        fun defaultConfigs(): Map<HomeSidePanelWidget, HomeSidePanelWidgetConfig> {
            val configs = mutableMapOf<HomeSidePanelWidget, HomeSidePanelWidgetConfig>()
            entries.forEachIndexed { index, widget ->
                val isDefaultEnabled = widget in DEFAULT_ENABLED_ORDER
                val defaultOrder = if (isDefaultEnabled) {
                    DEFAULT_ENABLED_ORDER.indexOf(widget)
                } else {
                    DEFAULT_ENABLED_ORDER.size + index
                }
                configs[widget] = HomeSidePanelWidgetConfig(
                    enabled = isDefaultEnabled,
                    order = defaultOrder,
                    tintColor = null,
                    customImagePath = null,
                    customText = null,
                )
            }
            return configs
        }
    }
}

/**
 * 单个组件的配置
 */
internal data class HomeSidePanelWidgetConfig(
    val enabled: Boolean = true,
    val order: Int = 0,
    val tintColor: Long? = null,
    val customImagePath: String? = null,
    val customText: String? = null,
) {
    fun withEnabled(enabled: Boolean) = copy(enabled = enabled)
    fun withOrder(order: Int) = copy(order = order)
    fun withTintColor(color: Long?) = copy(tintColor = color)
    fun withCustomImagePath(path: String?) = copy(customImagePath = path)
    fun withCustomText(text: String?) = copy(customText = text)
}

/**
 * 获取按顺序排列的已启用组件列表
 */
internal fun Map<HomeSidePanelWidget, HomeSidePanelWidgetConfig>.sortedEnabled(): List<HomeSidePanelWidget> {
    return this.entries
        .filter { it.value.enabled }
        .sortedBy { it.value.order }
        .map { it.key }
}

/**
 * 获取按顺序排列的所有组件（包括禁用的）
 */
internal fun Map<HomeSidePanelWidget, HomeSidePanelWidgetConfig>.sortedAll(): List<HomeSidePanelWidget> {
    return this.entries
        .sortedBy { it.value.order }
        .map { it.key }
}

/**
 * 交换两个组件的顺序
 */
internal fun Map<HomeSidePanelWidget, HomeSidePanelWidgetConfig>.swapOrder(
    widget1: HomeSidePanelWidget,
    widget2: HomeSidePanelWidget,
): Map<HomeSidePanelWidget, HomeSidePanelWidgetConfig> {
    val config1 = this[widget1] ?: return this
    val config2 = this[widget2] ?: return this
    return this.toMutableMap().apply {
        this[widget1] = config1.withOrder(config2.order)
        this[widget2] = config2.withOrder(config1.order)
    }
}

/**
 * 移动组件到指定位置（用于拖拽排序）
 */
internal fun Map<HomeSidePanelWidget, HomeSidePanelWidgetConfig>.moveWidget(
    widget: HomeSidePanelWidget,
    targetIndex: Int,
): Map<HomeSidePanelWidget, HomeSidePanelWidgetConfig> {
    val sorted = this.sortedAll().toMutableList()
    val currentIndex = sorted.indexOf(widget)
    if (currentIndex == -1 || currentIndex == targetIndex) return this
    sorted.removeAt(currentIndex)
    sorted.add(targetIndex.coerceIn(0, sorted.size), widget)
    return this.toMutableMap().apply {
        sorted.forEachIndexed { index, w ->
            this[w] = (this[w] ?: HomeSidePanelWidgetConfig()).withOrder(index)
        }
    }
}
