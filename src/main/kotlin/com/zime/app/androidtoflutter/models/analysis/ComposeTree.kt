package com.zime.app.androidtoflutter.models.analysis

import kotlinx.serialization.Serializable

/**
 * Compose UI 트리 구조
 */
@Serializable
data class ComposeTree(
    val root: ComposeNode,
    val stateBindings: List<StateBinding> = emptyList(),
    val eventHandlers: List<EventHandler> = emptyList()
)

@Serializable
sealed class ComposeNode {
    abstract val id: String
    abstract val children: List<ComposeNode>
    abstract val modifiers: List<ModifierInfo>
    abstract val sourceLocation: SourceLocation
    
    @Serializable
    data class Container(
        override val id: String,
        val type: ContainerType,
        val arrangement: ArrangementInfo? = null,
        val alignment: AlignmentInfo? = null,
        override val modifiers: List<ModifierInfo> = emptyList(),
        override val children: List<ComposeNode> = emptyList(),
        override val sourceLocation: SourceLocation
    ) : ComposeNode()
    
    @Serializable
    data class Widget(
        override val id: String,
        val type: WidgetType,
        val properties: Map<String, PropertyValue> = emptyMap(),
        override val modifiers: List<ModifierInfo> = emptyList(),
        override val children: List<ComposeNode> = emptyList(),
        override val sourceLocation: SourceLocation
    ) : ComposeNode()
    
    @Serializable
    data class Custom(
        override val id: String,
        val composableName: String,
        val parameters: List<ParameterValue> = emptyList(),
        override val modifiers: List<ModifierInfo> = emptyList(),
        override val children: List<ComposeNode> = emptyList(),
        override val sourceLocation: SourceLocation
    ) : ComposeNode()
}

@Serializable
enum class ContainerType {
    COLUMN,
    ROW,
    BOX,
    LAZY_COLUMN,
    LAZY_ROW,
    LAZY_VERTICAL_GRID,
    SCAFFOLD,
    SURFACE,
    CARD,
    CONSTRAINT_LAYOUT,
    ANIMATED_VISIBILITY,
    CROSSFADE
}

@Serializable
enum class WidgetType {
    TEXT,
    BUTTON,
    TEXT_BUTTON,
    OUTLINED_BUTTON,
    ICON_BUTTON,
    FLOATING_ACTION_BUTTON,
    TEXT_FIELD,
    OUTLINED_TEXT_FIELD,
    CHECKBOX,
    SWITCH,
    SLIDER,
    RADIO_BUTTON,
    IMAGE,
    ICON,
    DIVIDER,
    SPACER,
    CIRCULAR_PROGRESS,
    LINEAR_PROGRESS,
    DROPDOWN_MENU,
    DIALOG,
    BOTTOM_SHEET,
    SNACKBAR
}

@Serializable
data class ModifierInfo(
    val type: ModifierType,
    val parameters: Map<String, String> = emptyMap()
)

@Serializable
enum class ModifierType {
    PADDING,
    MARGIN,
    SIZE,
    WIDTH,
    HEIGHT,
    FILL_MAX_WIDTH,
    FILL_MAX_HEIGHT,
    FILL_MAX_SIZE,
    BACKGROUND,
    BORDER,
    CLIP,
    CLICKABLE,
    SELECTABLE,
    SCROLLABLE,
    ALPHA,
    ROTATE,
    SCALE,
    OFFSET,
    WEIGHT,
    ALIGN,
    SHADOW,
    ZINDEX
}

@Serializable
data class ArrangementInfo(
    val type: String, // "spacedBy", "Center", "SpaceBetween", etc.
    val value: String? = null
)

@Serializable
data class AlignmentInfo(
    val horizontal: String? = null,
    val vertical: String? = null
)

@Serializable
data class PropertyValue(
    val name: String,
    val value: String,
    val type: PropertyValueType
)

@Serializable
enum class PropertyValueType {
    STRING,
    NUMBER,
    BOOLEAN,
    COLOR,
    RESOURCE,
    LAMBDA,
    COMPOSABLE_LAMBDA,
    REFERENCE
}

@Serializable
data class ParameterValue(
    val name: String?,
    val value: String,
    val type: PropertyValueType
)

@Serializable
data class SourceLocation(
    val filePath: String,
    val startLine: Int,
    val endLine: Int,
    val startColumn: Int = 0,
    val endColumn: Int = 0
)

@Serializable
data class StateBinding(
    val stateName: String,
    val stateType: StateType,
    val boundWidgets: List<String> // Widget IDs
)

@Serializable
data class EventHandler(
    val eventName: String,
    val handlerCode: String,
    val sourceWidgetId: String
)
