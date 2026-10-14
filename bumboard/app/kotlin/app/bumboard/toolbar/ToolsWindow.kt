package app.bumboard.toolbar

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.input.dependency.theme
import org.fcitx.fcitx5.android.input.wm.InputWindow

/**
 * Gboard-style tools panel, shown in place of the keyboard.
 * Tap a tool to use it, tap its pin to add it to / remove it from the toolbar,
 * long-press and drag to reorder (the toolbar follows this order).
 */
class ToolsWindow : InputWindow.ExtendedInputWindow<ToolsWindow>() {

    private val theme by manager.theme()

    override val title: String get() = "Tools"

    private val tools = ToolPrefs.order.toMutableList()

    private inner class TileHolder(val tile: Tile) : RecyclerView.ViewHolder(tile)

    private val adapter = object : RecyclerView.Adapter<TileHolder>() {
        override fun getItemCount() = tools.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) = TileHolder(Tile(parent.context as android.content.Context, theme))
        override fun onBindViewHolder(holder: TileHolder, position: Int) {
            val tool = tools[position]
            holder.tile.bind(tool, tool in ToolPrefs.pinned)
            holder.tile.setOnClickListener {
                val keepsWindow = BumToolbar.run(tool)
                if (!keepsWindow) BumToolbar.backToKeyboard()
            }
            holder.tile.pin.setOnClickListener {
                ToolPrefs.togglePin(tool)
                notifyItemChanged(holder.bindingAdapterPosition)
                BumToolbar.rebuild()
            }
        }
    }

    private val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END, 0
    ) {
        override fun onMove(rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder): Boolean {
            val a = from.bindingAdapterPosition
            val b = to.bindingAdapterPosition
            tools.add(b, tools.removeAt(a))
            adapter.notifyItemMoved(a, b)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun clearView(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(rv, viewHolder)
            ToolPrefs.order = tools
            BumToolbar.rebuild()
        }
    })

    private val view by lazy {
        RecyclerView(context).apply {
            layoutManager = GridLayoutManager(context, 4)
            adapter = this@ToolsWindow.adapter
            val p = (resources.displayMetrics.density * 8).toInt()
            setPadding(p, p, p, p)
            clipToPadding = false
            touchHelper.attachToRecyclerView(this)
        }
    }

    override fun onCreateView(): View = view

    override fun onAttached() {}

    override fun onDetached() {}

    /** One tool: tonal circle with the icon, the name below, a pin in the corner. */
    @SuppressLint("ViewConstructor")
    class Tile(ctx: android.content.Context, private val theme: Theme) : FrameLayout(ctx) {
        private val d = ctx.resources.displayMetrics.density
        private val icon = ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val p = (12 * d).toInt()
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(theme.keyBackgroundColor)
            }
            imageTintList = ColorStateList.valueOf(theme.keyTextColor)
        }
        private val label = TextView(ctx).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            setTextColor(theme.keyTextColor)
            gravity = Gravity.CENTER
            maxLines = 2
        }
        val pin = ImageView(ctx).apply {
            val p = (6 * d).toInt()
            setPadding(p, p, p, p)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
        }

        init {
            val column = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                addView(icon, LinearLayout.LayoutParams((52 * d).toInt(), (52 * d).toInt()))
                addView(label, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply {
                    topMargin = (6 * d).toInt()
                })
            }
            addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = (10 * d).toInt(); bottomMargin = (10 * d).toInt()
            })
            addView(pin, LayoutParams((28 * d).toInt(), (28 * d).toInt(), Gravity.TOP or Gravity.END))
            isClickable = true
            isFocusable = true
            background = GradientDrawable().apply {
                cornerRadius = 20 * d
                setColor(0)
            }
            layoutParams = RecyclerView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        }

        fun bind(tool: Tool, pinned: Boolean) {
            icon.setImageResource(tool.icon)
            label.text = tool.title
            pin.setImageResource(if (pinned) R.drawable.ms_pin_fill else R.drawable.ms_pin)
            pin.imageTintList = ColorStateList.valueOf(
                if (pinned) theme.genericActiveBackgroundColor
                else ColorUtils.setAlphaComponent(theme.keyTextColor, 0x80)
            )
            pin.contentDescription = if (pinned) "Remove ${tool.title} from toolbar" else "Add ${tool.title} to toolbar"
        }
    }
}
