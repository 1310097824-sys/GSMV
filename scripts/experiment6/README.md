# 实验六测试脚本说明

本目录保留实验六报告对应的可执行测试脚本。脚本不会保存 API Key；真实系统测试需要在当前 PowerShell 会话中通过环境变量提供：

```powershell
$env:BAILIAN_API_KEY="你的阿里云百炼Key"
$env:DEEPSEEK_API_KEY="你的DeepSeekKey"
```

CMD 脚本入口：

- `run_whitebox_excel.cmd`：执行 JUnit 白盒单元测试并生成 `01_白盒单元测试执行报告.xlsx`。
- `run_functional_excel.cmd`：调用真实后端接口执行功能测试并生成 `02_功能测试执行报告.xlsx`。
- `run_performance_excel.cmd`：执行多轮性能采样并生成 `03_性能测试执行报告.xlsx`。
- `run_integration_excel.cmd`：执行跨模块真实联调并生成 `04_真实系统联调执行报告.xlsx`。
- `run_all_excel_reports.cmd`：先执行白盒测试，再启动系统，依次执行功能、性能、联调测试，最后停止系统。

PowerShell 脚本入口（备用）：

- `run_whitebox_excel.ps1`：执行 JUnit 白盒单元测试并生成 `01_白盒单元测试执行报告.xlsx`。
- `run_functional_excel.ps1`：调用真实后端接口执行功能测试并生成 `02_功能测试执行报告.xlsx`。
- `run_performance_excel.ps1`：执行多轮性能采样并生成 `03_性能测试执行报告.xlsx`。
- `run_integration_excel.ps1`：执行跨模块真实联调并生成 `04_真实系统联调执行报告.xlsx`。
- `run_all_excel_reports.ps1`：先执行白盒测试，再启动系统，依次执行功能、性能、联调测试，最后停止系统。

默认 Excel 输出目录：

`D:\质量测试与保证实验\实验六\测试执行Excel报告`

可通过环境变量覆盖：

```powershell
$env:GSMV_EXPERIMENT6_REPORT_DIR="D:\质量测试与保证实验\实验六\测试执行Excel报告"
$env:GSMV_TEST_IMAGE="C:\Users\13100\Pictures\OIP.jpg"
```
