package display.interactive.whiteboard.impl

import android.content.Context
import android.view.View
import android.view.ViewGroup
import display.interactive.whiteboard.element.BaseElement
import display.interactive.whiteboard.element.WhiteBoardState
import kotlin.reflect.KClass

class NativeViewHostLayer(
    private val context: Context,
    private val container: ViewGroup
) {
    interface Adapter<T : BaseElement> {
        val elementClass: KClass<T>
        fun createView(context: Context): View
        fun bindView(view: View, element: T, state: WhiteBoardState)
        fun onViewRemoved(view: View) {}
    }

    private data class Holder(
        val adapter: Adapter<out BaseElement>,
        val view: View
    )

    private val adapters = mutableListOf<Adapter<out BaseElement>>()
    private val holders = linkedMapOf<String, Holder>()

    fun registerAdapter(adapter: Adapter<out BaseElement>) {
        adapters.add(adapter)
    }

    fun sync(state: WhiteBoardState) {
        val currentIds = state.elements.map { it.id }.toSet()
        val removeIds = holders.keys.filter { it !in currentIds }
        removeIds.forEach { id ->
            val holder = holders.remove(id) ?: return@forEach
            holder.adapter.onViewRemoved(holder.view)
            container.removeView(holder.view)
        }

        state.elements.forEach { element ->
            val adapter = adapters.firstOrNull { it.elementClass.isInstance(element) } ?: return@forEach
            val holder = holders[element.id]
            val view = if (holder == null) {
                val created = adapter.createView(context)
                container.addView(created)
                holders[element.id] = Holder(adapter, created)
                created
            } else {
                holder.view
            }
            @Suppress("UNCHECKED_CAST")
            (adapter as Adapter<BaseElement>).bindView(view, element, state)
        }
    }
}
