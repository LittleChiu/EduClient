# EduClient Go

浙江理工大学教务系统原生桌面客户端。当前 `main` 分支使用 Go 与 Fyne 实现，窗口标题包含“资料分享群659502480”。

Java 旧版保存在 [`legacy-java`](https://github.com/LittleChiu/EduClient/tree/legacy-java) 分支。

## 功能

- 内置 WebView2 统一认证登录，进入教务系统后自动提取并校验 Cookie。
- 成绩查询、统计与 CSV 导出。
- 课程分类与教学班查询。
- 批量抢课、并发请求、单次请求超时和候选轮询时长。
- 同一学期、同一大课代码自动分组，按输入顺序尝试教学班；满额时切换下一候选，成功后停止该课程组。

## 本地构建

```powershell
cd go-client
go test ./...
go build -trimpath -ldflags "-s -w -H=windowsgui" -o "dist\EduClient-Go-同课优先级版.exe" .\cmd\educlient
```

在仓库根目录运行 `start-go.cmd` 可直接启动已构建程序；未找到成品时会回退到 `go run`。

## GitHub Actions

向 `main` 推送代码会执行测试并生成 `EduClient-Go-Windows` 构建产物。推送 `v*` 标签时会同时创建 GitHub Release。

