package display.interactive.whiteboard.element

import android.graphics.RectF

/**
 * 一个用于空间索引的四叉树实现，用于快速检索指定区域内的元素。
 */
class QuadTree(
    private val bounds: RectF,
    private val capacity: Int = 10,
    private val maxLevel: Int = 5,
    private val level: Int = 0
) {
    private val elements = mutableListOf<BaseElement>()
    private var divided = false
    
    private var topLeft: QuadTree? = null
    private var topRight: QuadTree? = null
    private var bottomLeft: QuadTree? = null
    private var bottomRight: QuadTree? = null

    /**
     * 插入一个元素到四叉树中。
     */
    fun insert(element: BaseElement): Boolean {
        if (!RectF.intersects(bounds, element.getBounds())) {
            return false
        }

        if (elements.size < capacity || level >= maxLevel) {
            elements.add(element)
            return true
        }

        if (!divided) {
            subdivide()
        }

        return (topLeft?.insert(element) ?: false) ||
                (topRight?.insert(element) ?: false) ||
                (bottomLeft?.insert(element) ?: false) ||
                (bottomRight?.insert(element) ?: false)
    }

    private fun subdivide() {
        val x = bounds.left
        val y = bounds.top
        val w = bounds.width() / 2
        val h = bounds.height() / 2

        topLeft = QuadTree(RectF(x, y, x + w, y + h), capacity, maxLevel, level + 1)
        topRight = QuadTree(RectF(x + w, y, x + 2 * w, y + h), capacity, maxLevel, level + 1)
        bottomLeft = QuadTree(RectF(x, y + h, x + w, y + 2 * h), capacity, maxLevel, level + 1)
        bottomRight = QuadTree(RectF(x + w, y + h, x + 2 * w, y + 2 * h), capacity, maxLevel, level + 1)
        
        divided = true
    }

    /**
     * 查询与指定区域相交的所有元素。
     */
    fun query(range: RectF, result: MutableSet<BaseElement>) {
        if (!RectF.intersects(bounds, range)) {
            return
        }

        for (element in elements) {
            if (RectF.intersects(element.getBounds(), range)) {
                result.add(element)
            }
        }

        if (divided) {
            topLeft?.query(range, result)
            topRight?.query(range, result)
            bottomLeft?.query(range, result)
            bottomRight?.query(range, result)
        }
    }

    /**
     * 清空四叉树。
     */
    fun clear() {
        elements.clear()
        topLeft = null
        topRight = null
        bottomLeft = null
        bottomRight = null
        divided = false
    }
}
