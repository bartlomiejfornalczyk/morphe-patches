package app.template.patches.shared

/**
 * This class provides standardized UI components for Morphe patches.
 * It ensures that patch UI elements have a consistent appearance
 * and behavior across different patches.
 */
object StandardizedUIComponents {
    // Grid layout constants
    const val GRID_3X3 = "3x3"
    const val GRID_4X4 = "4x4"
    
    // UI styling constants for consistent patch appearance
    const val STANDARD_BACKGROUND_COLOR = 0xFFFFFFFF.toInt()  // White background
    const val STANDARD_TEXT_COLOR = 0xFF000000.toInt()        // Black text
    const val STANDARD_BORDER_COLOR = 0xFFCCCCCC.toInt()      // Light gray border
    
    // Default grid configurations
    val DEFAULT_GRID_CONFIGURATIONS = mapOf(
        GRID_3X3 to 3,
        GRID_4X4 to 4
    )
    
    // UI element definitions
    object UIElements {
        const val CARD_VIEW = "com.google.android.material.card.MaterialCardView"
        const val TEXT_VIEW = "android.widget.TextView"
        const val BUTTON = "android.widget.Button"
        const val SWITCH = "android.widget.Switch"
    }
    
    // Standardized patch styling
    object Styles {
        const val PATCH_CARD_STYLE = "standard_patch_card"
        const val PATCH_HEADER_STYLE = "standard_patch_header"
        const val PATCH_CONTENT_STYLE = "standard_patch_content"
        const val PATCH_FOOTER_STYLE = "standard_patch_footer"
    }
}