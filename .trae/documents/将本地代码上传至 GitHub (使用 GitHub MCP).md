## 任务目标
将本地 Android 项目 `WhiteBoard4` 上传到 GitHub 仓库，要求使用 GitHub MCP 工具。

## 实施步骤

### 1. 文件清理与准备
- **识别待上传文件**：遍历项目目录，根据根目录及子模块中的 `.gitignore` 文件，过滤掉不需要上传的内容（如 `.gradle/`, `build/`, `local.properties`, `.idea/` 中的缓存等）。
- **核对项目结构**：确认包含 `app`, `accelerate`, `boardelement`, `boardimpl`, `boardinterface`, `boardpersist` 等核心模块及 `docs`, `gradle` 等配置。

### 2. GitHub 仓库管理
- **创建仓库**：尝试使用 `mcp_GitHub_create_repository` 在您的 GitHub 账号下创建名为 `WhiteBoard4` 的新仓库。
- **权限校验**：如果创建失败（例如权限不足），将提示您检查 Token 权限或手动创建仓库并提供信息。

### 3. 代码上传 (使用 GitHub MCP)
- **批量上传**：使用 `mcp_GitHub_push_files` 工具，将过滤后的本地代码文件分批次上传到 GitHub 仓库的 `main` 分支。
- **提交信息**：使用统一的提交信息，如 "Initial commit of WhiteBoard4 project"。

### 4. 结果验证
- **确认状态**：上传完成后，通过 MCP 工具列出仓库文件，确保所有核心代码已成功同步。
- **提供链接**：向您提供 GitHub 仓库的访问链接。

## 注意事项
- 由于项目文件较多，我会采用分批上传的方式以确保稳定性。
- 如果您的 GitHub Token 权限受限，请确保已授予 `repo` 作用域。

您是否同意按照此方案执行？