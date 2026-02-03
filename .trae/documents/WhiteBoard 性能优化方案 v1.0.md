## WhiteBoard 性能优化实施方案 v1.1

### 1. 空间索引优化 (QuadTree)
- **目标**: 解决橡皮擦卡顿和全量遍历性能问题。
- **步骤**:
    - 在 `boardelement` 模块实现 `QuadTree` 类，用于管理元素包围盒。
    - 在 `WhiteBoardSDKImpl` 中同步维护索引状态。
    - 重构 `eraseAt` 方法，通过 `QuadTree` 快速定位受影响元素。

### 2. 可配置路径简化 (Path Simplification)
- **目标**: 减少冗余点，提升处理速度，同时保留灵活性。
- **步骤**:
    - 在 `StrokeElement` 中增加 `isSimplificationEnabled` 属性（默认 `true`）。
    - 实现 Ramer-Douglas-Peucker (RDP) 算法。
    - 在书写结束提交笔迹时，根据配置执行路径简化。

### 3. 渲染性能升级
- **目标**: 解决缩放、移动和高密度显示下的卡顿。
- **步骤**:
    - **消除实时排序**: 在状态更新时预排 `zIndex`，不再在 `draw` 循环中排序。
    - **视口剔除 (Frustum Culling)**: 利用 `QuadTree` 只绘制当前可见范围内的元素。
    - **离屏缓存机制**: 为不常变动的元素层引入 `Bitmap` 缓存，加速缩放和平移时的响应。

### 4. 渲染细节优化
- 减少渲染路径中的临时对象分配（如 `RectF` 复用）。
- 优化 `StrokeElement.erase` 的点拆分逻辑。

请确认是否开始执行？我将首先从 **QuadTree 空间索引** 和 **橡皮擦优化** 入手。