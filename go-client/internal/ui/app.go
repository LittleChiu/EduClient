package ui

import (
	"context"
	"encoding/csv"
	"fmt"
	"math/rand"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"fyne.io/fyne/v2"
	"fyne.io/fyne/v2/app"
	"fyne.io/fyne/v2/container"
	"fyne.io/fyne/v2/dialog"
	"fyne.io/fyne/v2/theme"
	"fyne.io/fyne/v2/widget"

	"github.com/LittleChiu/EduClient/go-client/internal/jwgl"
	"github.com/LittleChiu/EduClient/go-client/internal/login"
	"github.com/LittleChiu/EduClient/go-client/internal/model"
)

type DesktopApp struct {
	app    fyne.App
	window fyne.Window
	client *jwgl.Client
	status *widget.Label

	grades         []model.GradeRecord
	tabs           []model.CourseTab
	courses        []model.CourseItem
	activeTab      model.CourseTab
	selectedCourse int
}

// logView uses a normal themed label instead of a disabled Entry. Disabled
// Entry text can become nearly invisible with dark desktop themes.
type logView struct {
	label  *widget.Label
	scroll *container.Scroll
	text   string
}

type batchTarget struct {
	name       string
	candidates []string
	done       atomic.Bool
	inactive   atomic.Bool
	ctx        context.Context
	cancel     context.CancelFunc
	mu         sync.Mutex
	next       int
}

type batchCandidateRound struct {
	target *batchTarget
	name   string
	index  int
	ctx    context.Context
	cancel context.CancelFunc
	full   atomic.Bool
}

type batchJob struct {
	target        *batchTarget
	round         *batchCandidateRound
	tab           model.CourseTab
	course        model.CourseItem
	jxbIDs        string
	courseContext model.CourseContext
}

var teachingClassNamePattern = regexp.MustCompile(`^\(([^)]+)\)-([^-]+)-(.+)$`)

func newLogView() *logView {
	label := widget.NewLabel("")
	label.Wrapping = fyne.TextWrapWord
	label.TextStyle = fyne.TextStyle{Monospace: true}
	scroll := container.NewVScroll(container.NewPadded(label))
	scroll.SetMinSize(fyne.NewSize(620, 380))
	return &logView{label: label, scroll: scroll}
}

func (view *logView) Clear() {
	view.text = ""
	view.label.SetText("")
	view.scroll.ScrollToTop()
}

func (view *logView) Append(line string) {
	view.text += line
	view.label.SetText(view.text)
	view.scroll.ScrollToBottom()
}

func New(client *jwgl.Client) *DesktopApp {
	a := app.NewWithID("cn.edu.zstu.educlient.go")
	w := a.NewWindow("EduClient Go｜资料分享群659502480")
	w.Resize(fyne.NewSize(1180, 760))
	return &DesktopApp{app: a, window: w, client: client, status: widget.NewLabel("就绪"), selectedCourse: -1}
}

func (d *DesktopApp) Run() {
	tabs := container.NewAppTabs(
		container.NewTabItemWithIcon("登录与会话", theme.AccountIcon(), d.sessionPage()),
		container.NewTabItemWithIcon("成绩查询", theme.DocumentIcon(), d.gradePage()),
		container.NewTabItemWithIcon("选课查询", theme.SearchIcon(), d.coursePage()),
		container.NewTabItemWithIcon("批量抢课", theme.MediaPlayIcon(), d.batchPage()),
	)
	tabs.SetTabLocation(container.TabLocationTop)
	d.window.SetContent(container.NewBorder(nil, container.NewPadded(d.status), nil, nil, tabs))
	d.window.ShowAndRun()
}

func (d *DesktopApp) sessionPage() fyne.CanvasObject {
	cookie := widget.NewPasswordEntry()
	cookie.Disable()
	cookie.SetPlaceHolder("登录成功后自动获取")
	state := widget.NewLabel("未设置会话")
	state.Wrapping = fyne.TextWrapWord
	endpointByName := map[string]login.Endpoint{
		login.DirectEndpoint.Name: login.DirectEndpoint,
		login.WebVPNEndpoint.Name: login.WebVPNEndpoint,
	}
	endpointSelect := widget.NewSelect([]string{login.DirectEndpoint.Name, login.WebVPNEndpoint.Name}, nil)
	endpointSelect.SetSelected(login.DirectEndpoint.Name)

	var openLogin *widget.Button
	openLogin = widget.NewButtonWithIcon("内置登录", theme.LoginIcon(), func() {
		endpoint := endpointByName[endpointSelect.Selected]
		openLogin.Disable()
		endpointSelect.Disable()
		state.SetText("正在打开" + endpoint.Name + "登录窗口…")
		d.setStatus("等待完成统一认证")
		go func() {
			value, err := login.Open(context.Background(), endpoint)
			if err == nil {
				d.client.SetSession(endpoint.BaseURL, value)
				err = d.client.ValidateSession(context.Background())
			}
			fyne.Do(func() {
				openLogin.Enable()
				endpointSelect.Enable()
				if err != nil {
					state.SetText("登录未完成：" + err.Error())
					d.setStatus("未登录")
					return
				}
				cookie.SetText(value)
				state.SetText("登录成功，已自动获取并校验 " + endpoint.BaseURL + " 会话 Cookie。")
				d.setStatus("已登录")
			})
		}()
	})
	validate := widget.NewButtonWithIcon("校验当前会话", theme.ConfirmIcon(), func() {
		if !d.client.LoggedIn() {
			d.showError("未登录", fmt.Errorf("请先完成内置登录"))
			return
		}
		state.SetText("正在校验登录状态…")
		d.setStatus("正在校验会话")
		go func() {
			err := d.client.ValidateSession(context.Background())
			fyne.Do(func() {
				if err != nil {
					state.SetText("会话校验失败：" + err.Error())
					d.setStatus("会话无效")
				} else {
					state.SetText("会话有效，成绩与选课功能已可使用。")
					d.setStatus("已登录")
				}
			})
		}()
	})
	copyHint := widget.NewRichTextFromMarkdown("**内置登录流程**：选择 `教务系统直连` 或 `学校 WebVPN` 后点击“内置登录”。页面进入教务系统首页后，程序会从对应域名读取 Cookie，并让成绩查询、选课查询和批量抢课使用同一域名。")
	copyHint.Wrapping = fyne.TextWrapWord
	return container.NewPadded(container.NewVBox(
		widget.NewLabelWithStyle("登录与会话", fyne.TextAlignLeading, fyne.TextStyle{Bold: true}),
		copyHint,
		container.NewHBox(widget.NewLabel("登录线路"), endpointSelect, openLogin, validate),
		widget.NewSeparator(),
		widget.NewLabel("会话 Cookie（已隐藏）"), cookie, state,
	))
}

func (d *DesktopApp) gradePage() fyne.CanvasObject {
	years := academicYears()
	year := widget.NewSelect(years, nil)
	year.SetSelected("全部")
	semester := widget.NewSelect([]string{"全部", "第一学期", "第二学期", "第三学期"}, nil)
	semester.SetSelected("全部")
	summary := widget.NewLabel("尚未查询")
	data := make([][]string, 0)
	headers := []string{"学年", "学期", "课程代码", "课程名称", "性质", "学分", "成绩", "绩点", "成绩性质", "学位课", "开课学院", "教师", "考核方式"}
	table := widget.NewTableWithHeaders(
		func() (int, int) { return len(data), len(headers) },
		func() fyne.CanvasObject { return widget.NewLabel("…") },
		func(id widget.TableCellID, object fyne.CanvasObject) {
			object.(*widget.Label).SetText(data[id.Row][id.Col])
		},
	)
	for i, text := range headers {
		table.SetColumnWidth(i, float32(max(72, len([]rune(text))*24)))
	}
	table.SetColumnWidth(3, 180)
	table.SetColumnWidth(10, 160)
	table.SetColumnWidth(11, 120)

	var query *widget.Button
	query = widget.NewButtonWithIcon("查询成绩", theme.SearchIcon(), func() {
		if !d.requireLogin() {
			return
		}
		query.Disable()
		d.setStatus("正在查询成绩")
		go func() {
			records, err := d.client.FetchGrades(context.Background(), yearValue(year.Selected), termValue(semester.Selected))
			fyne.Do(func() {
				query.Enable()
				if err != nil {
					d.showError("成绩查询失败", err)
					d.setStatus("成绩查询失败")
					return
				}
				d.grades = records
				data = make([][]string, len(records))
				for i, record := range records {
					data[i] = record.Row()
				}
				table.Refresh()
				avg, gpa := gradeMetrics(records)
				summary.SetText(fmt.Sprintf("共 %d 门课程　平均分：%s　加权平均绩点：%s", len(records), avg, gpa))
				d.setStatus("成绩查询完成")
			})
		}()
	})
	export := widget.NewButtonWithIcon("导出 CSV", theme.FileIcon(), func() { d.exportGrades() })
	filter := container.NewHBox(widget.NewLabel("学年"), year, widget.NewLabel("学期"), semester, query, export)
	return container.NewBorder(filter, summary, nil, nil, table)
}

func (d *DesktopApp) coursePage() fyne.CanvasObject {
	category := widget.NewSelect(nil, nil)
	keyword := widget.NewEntry()
	keyword.SetPlaceHolder("课程名称或课程代码")
	selected := widget.NewLabel("未选择教学班")
	data := make([][]string, 0)
	headers := []string{"课程代码", "课程名称", "教学班", "学分", "已选/容量", "教师", "上课时间", "JXB ID"}
	table := widget.NewTableWithHeaders(
		func() (int, int) { return len(data), len(headers) },
		func() fyne.CanvasObject { return widget.NewLabel("…") },
		func(id widget.TableCellID, object fyne.CanvasObject) {
			object.(*widget.Label).SetText(data[id.Row][id.Col])
		},
	)
	for i := range headers {
		table.SetColumnWidth(i, 100)
	}
	table.SetColumnWidth(1, 180)
	table.SetColumnWidth(2, 150)
	table.SetColumnWidth(5, 180)
	table.SetColumnWidth(6, 220)
	table.SetColumnWidth(7, 150)
	table.OnSelected = func(id widget.TableCellID) {
		if id.Row >= 0 && id.Row < len(d.courses) {
			d.selectedCourse = id.Row
			c := d.courses[id.Row]
			selected.SetText("已选择：" + c.Kcmc + " / " + c.Jxbmc)
		}
	}
	var refresh *widget.Button
	refresh = widget.NewButtonWithIcon("获取选课分类", theme.ViewRefreshIcon(), func() {
		if !d.requireLogin() {
			return
		}
		refresh.Disable()
		d.setStatus("正在获取选课分类")
		go func() {
			tabs, err := d.client.FetchCourseTabs(context.Background())
			fyne.Do(func() {
				refresh.Enable()
				if err != nil {
					d.showError("获取选课分类失败", err)
					return
				}
				d.tabs = tabs
				names := make([]string, len(tabs))
				for i, tab := range tabs {
					names[i] = tab.Name
				}
				category.Options = names
				category.Refresh()
				if len(names) > 0 {
					category.SetSelected(names[0])
				}
				d.setStatus(fmt.Sprintf("已获取 %d 个课程分类", len(names)))
			})
		}()
	})
	var search *widget.Button
	search = widget.NewButtonWithIcon("查询课程", theme.SearchIcon(), func() {
		if !d.requireLogin() {
			return
		}
		if category.Selected == "" {
			d.showError("未选择分类", fmt.Errorf("请先获取并选择课程分类"))
			return
		}
		index := findTab(d.tabs, category.Selected)
		if index < 0 {
			return
		}
		tab := d.tabs[index]
		search.Disable()
		d.setStatus("正在查询课程")
		go func() {
			courses, err := d.client.FetchCourses(context.Background(), tab, keyword.Text)
			fyne.Do(func() {
				search.Enable()
				if err != nil {
					d.showError("课程查询失败", err)
					return
				}
				d.activeTab = tab
				d.courses = courses
				d.selectedCourse = -1
				data = make([][]string, len(courses))
				for i, course := range courses {
					data[i] = courseRow(course)
				}
				table.Refresh()
				selected.SetText(fmt.Sprintf("查询完成：%d 个教学班", len(courses)))
				d.setStatus("课程查询完成")
			})
		}()
	})
	filter := container.NewHBox(refresh, widget.NewLabel("分类"), category, widget.NewLabel("关键词"), container.NewGridWrap(fyne.NewSize(220, 36), keyword), search)
	return container.NewBorder(container.NewVBox(filter, selected), nil, nil, nil, table)
}

func (d *DesktopApp) batchPage() fyne.CanvasObject {
	info := widget.NewLabel("输入教学班名称（一行一个）。相同大课号自动分组，并按输入顺序作为候选优先级；前一个满额后切换到下一个。")
	info.Wrapping = fyne.TextWrapWord
	timeInfo := widget.NewLabel("单次超时限制每个 HTTP 请求；候选轮询时长限制当前候选的一轮轮询，-1 表示无限。有限时长配合循环模式会自动开启下一轮。")
	timeInfo.Wrapping = fyne.TextWrapWord
	targets := widget.NewMultiLineEntry()
	targets.SetPlaceHolder("例如：\n(2026-2027-1)-00998-12\n(2026-2027-1)-01401-01")
	targets.SetMinRowsVisible(8)
	timeout := widget.NewEntry()
	timeout.SetText("15000")
	workers := widget.NewEntry()
	workers.SetText("4")
	duration := widget.NewEntry()
	duration.SetText("30")
	interval := widget.NewEntry()
	interval.SetText("80")
	log := newLogView()
	var running atomic.Bool
	loop := widget.NewCheck("循环模式", nil)
	var start *widget.Button
	var stop *widget.Button
	start = widget.NewButtonWithIcon("开始批量抢课", theme.MediaPlayIcon(), func() {
		if !d.requireLogin() {
			return
		}
		keywords := splitTargets(targets.Text)
		if len(keywords) == 0 {
			d.showError("未输入教学班名称", fmt.Errorf("请输入至少一个教学班名称"))
			return
		}
		if !running.CompareAndSwap(false, true) {
			return
		}
		requestTimeout, err := positiveDuration(timeout.Text, time.Millisecond)
		if err != nil {
			running.Store(false)
			d.showError("超时参数错误", err)
			return
		}
		workerCount, err := positiveInt(workers.Text)
		if err != nil {
			running.Store(false)
			d.showError("并发参数错误", err)
			return
		}
		totalDuration, err := pollDuration(duration.Text)
		if err != nil {
			running.Store(false)
			d.showError("持续时间参数错误", err)
			return
		}
		intervalDuration, err := nonNegativeDuration(interval.Text, time.Millisecond)
		if err != nil {
			running.Store(false)
			d.showError("间隔参数错误", err)
			return
		}
		log.Clear()
		start.Disable()
		stop.Enable()
		d.setStatus("批量抢课运行中")
		go d.runBatchTargets(keywords, loop.Checked, workerCount, requestTimeout, totalDuration, intervalDuration, &running, log, start, stop)
	})
	stop = widget.NewButtonWithIcon("停止", theme.MediaStopIcon(), func() { running.Store(false); stop.Disable(); d.setStatus("正在停止批量抢课") })
	stop.Disable()
	grid := container.NewGridWithColumns(4,
		container.NewVBox(widget.NewLabel("单次超时(ms)"), timeout), container.NewVBox(widget.NewLabel("并发线程"), workers),
		container.NewVBox(widget.NewLabel("候选轮询时长(s，-1=无限)"), duration), container.NewVBox(widget.NewLabel("线程间隔(ms)"), interval),
	)
	inputPanel := container.NewVBox(widget.NewLabel("教学班名称列表（一行一个）"), targets)
	return container.NewBorder(container.NewVBox(info, timeInfo, grid, container.NewHBox(start, stop, loop), widget.NewSeparator()), nil, nil, container.NewPadded(inputPanel), log.scroll)
}

func (d *DesktopApp) runBatchTargets(keywords []string, loop bool, workers int, requestTimeout, total, interval time.Duration, running *atomic.Bool, log *logView, start, stop *widget.Button) {
	appendLog := func(message string) {
		fyne.Do(func() { log.Append(time.Now().Format("15:04:05") + "  " + message + "\n") })
	}
	targets := groupBatchTargets(keywords)
	defer func() {
		for _, target := range targets {
			target.cancel()
		}
	}()
	appendLog(fmt.Sprintf("开始批量抢课：%d 行候选，合并为 %d 个大课组｜并发 %d｜单次超时 %s｜候选持续 %s", len(keywords), len(targets), workers, requestTimeout, durationText(total)))
	for running.Load() && !allTargetsDone(targets) {
		jobs := d.resolveBatchJobs(targets, running, appendLog)
		if len(jobs) == 0 {
			if total < 0 || loop {
				appendLog("尚有目标未解析，1 秒后重新查询")
				waitWhileRunning(running, time.Second)
				continue
			}
			break
		}
		var wait sync.WaitGroup
		for _, job := range jobs {
			if job.target.done.Load() {
				continue
			}
			wait.Add(1)
			go func(current batchJob) {
				defer wait.Done()
				d.runCoursePoll(current, workers, requestTimeout, total, interval, running, appendLog)
			}(job)
		}
		wait.Wait()
		fallbackTriggered := false
		seenRounds := make(map[*batchCandidateRound]struct{})
		for _, job := range jobs {
			if _, seen := seenRounds[job.round]; seen {
				continue
			}
			seenRounds[job.round] = struct{}{}
			job.round.cancel()
			if job.round.full.Load() && !job.target.done.Load() {
				job.target.advanceAfter(job.round.index)
				fallbackTriggered = true
			} else if !job.target.done.Load() && total >= 0 && !loop {
				job.target.inactive.Store(true)
			}
		}
		if allTargetsDone(targets) || !running.Load() {
			break
		}
		if total >= 0 && !loop && !fallbackTriggered {
			break
		}
		appendLog(fmt.Sprintf("剩余 %d 门未成功，继续下一轮", remainingTargets(targets)))
	}
	completed := len(targets) - remainingTargets(targets)
	if completed == len(targets) {
		appendLog(fmt.Sprintf("全部 %d 门课程均已成功", completed))
	} else {
		appendLog(fmt.Sprintf("批量抢课停止：已成功 %d/%d，剩余 %d 门", completed, len(targets), len(targets)-completed))
	}
	running.Store(false)
	fyne.Do(func() {
		start.Enable()
		stop.Disable()
		d.setStatus("批量抢课结束")
		log.Append(time.Now().Format("15:04:05") + "  批量抢课结束\n")
	})
}

func (d *DesktopApp) resolveBatchJobs(targets []*batchTarget, running *atomic.Bool, appendLog func(string)) []batchJob {
	tabs, err := d.client.FetchCourseTabs(context.Background())
	if err != nil {
		appendLog("获取课程分类失败：" + err.Error())
		return nil
	}
	appendLog(fmt.Sprintf("已获取 %d 个课程分类", len(tabs)))
	jobs := make([]batchJob, 0)
	for _, target := range targets {
		if target.done.Load() || target.inactive.Load() || !running.Load() {
			continue
		}
		startIndex := target.nextPriority()
		if startIndex >= len(target.candidates) {
			target.resetPriority()
			startIndex = 0
		}
		selected := false
		for candidateIndex := startIndex; candidateIndex < len(target.candidates); candidateIndex++ {
			candidate := target.candidates[candidateIndex]
			appendLog(fmt.Sprintf("[%s] 检查优先级 %d：%s", target.name, candidateIndex+1, candidate))
			roundCtx, roundCancel := context.WithCancel(target.ctx)
			round := &batchCandidateRound{target: target, name: candidate, index: candidateIndex, ctx: roundCtx, cancel: roundCancel}
			candidateJobs := make([]batchJob, 0)
			foundExact := false
			for tabIndex := range tabs {
				tab := &tabs[tabIndex]
				if err := d.client.FetchCourseMetadata(context.Background(), tab); err != nil {
					appendLog(fmt.Sprintf("[%s] 初始化失败：%v", tab.Name, err))
					continue
				}
				courses, err := d.client.FetchCoursesForTab(context.Background(), tab, candidate)
				if err != nil {
					appendLog(fmt.Sprintf("[%s] 查询失败：%v", tab.Name, err))
					continue
				}
				for _, courseHead := range courses {
					details, err := d.client.FetchCourseDetails(context.Background(), *tab, candidate, courseHead.KchID, []model.CourseItem{courseHead})
					if err != nil {
						appendLog(fmt.Sprintf("课程 [%s] 详情查询失败：%v", courseHead.Kcmc, err))
						continue
					}
					for _, detail := range details {
						if detail.Jxbmc != "" && strings.TrimSpace(detail.Jxbmc) != candidate {
							continue
						}
						foundExact = true
						limit, _ := strconv.Atoi(strings.TrimSpace(detail.Jxbrl))
						used, _ := strconv.Atoi(strings.TrimSpace(detail.Yxzrs))
						if limit > 0 && used >= limit {
							appendLog(fmt.Sprintf("[%s] %s 已满 %d/%d，切换下一优先级", target.name, candidate, used, limit))
							continue
						}
						jxbIDs, smartErr := d.client.SmartJxbIDs(context.Background(), *tab, detail)
						if smartErr != nil {
							appendLog("关联子课程查询失败：" + smartErr.Error())
						}
						if detail.DoJxbID == "" {
							detail.DoJxbID = detail.JxbID
						}
						candidateJobs = append(candidateJobs, batchJob{target: target, round: round, tab: *tab, course: detail, jxbIDs: jxbIDs, courseContext: d.client.CurrentCourseContext()})
					}
				}
			}
			if len(candidateJobs) > 0 {
				appendLog(fmt.Sprintf("[%s] 激活候选：%s", target.name, candidate))
				jobs = append(jobs, candidateJobs...)
				selected = true
				break
			}
			roundCancel()
			if !foundExact {
				appendLog(fmt.Sprintf("[%s] 未找到候选：%s", target.name, candidate))
			}
		}
		if !selected {
			target.resetPriority()
			appendLog(fmt.Sprintf("[%s] 本轮没有可用候选，下轮重新按优先级检查", target.name))
		}
	}
	return jobs
}

func (d *DesktopApp) runCoursePoll(job batchJob, workers int, requestTimeout, total, interval time.Duration, running *atomic.Bool, appendLog func(string)) bool {
	var ctx context.Context
	var cancel context.CancelFunc
	if total < 0 {
		ctx, cancel = context.WithCancel(job.round.ctx)
	} else {
		ctx, cancel = context.WithTimeout(job.round.ctx, total)
	}
	defer cancel()
	var attempts, timeouts, failures atomic.Int64
	var success atomic.Bool
	appendLog(fmt.Sprintf("[%s] 开始轮询：%s｜并发 %d｜持续 %s", job.target.name, job.course.Jxbmc, workers, durationText(total)))
	done := make(chan struct{}, workers)
	for workerID := 1; workerID <= workers; workerID++ {
		go func(id int) {
			defer func() { done <- struct{}{} }()
			for running.Load() && ctx.Err() == nil && !job.target.done.Load() && !job.round.full.Load() && !success.Load() {
				jxbIDs := job.course.DoJxbID
				if job.jxbIDs != "" {
					jxbIDs = job.jxbIDs
				}
				ok, body, err := d.client.EnrollWithCourseContext(ctx, job.tab, job.course, jxbIDs, job.courseContext, requestTimeout)
				if err != nil {
					if job.target.done.Load() {
						return
					}
					if ctx.Err() == context.DeadlineExceeded || strings.Contains(err.Error(), "deadline") || strings.Contains(err.Error(), "超时") {
						timeouts.Add(1)
					} else {
						failures.Add(1)
					}
					failureCount := failures.Load() + timeouts.Load()
					if failureCount <= 3 || failureCount%10 == 0 {
						appendLog(fmt.Sprintf("线程 %d：请求异常(%d)：%v", id, failureCount, err))
					}
				} else {
					n := attempts.Add(1)
					if ok {
						success.Store(true)
						if job.target.done.CompareAndSwap(false, true) {
							appendLog(fmt.Sprintf("[%s] 抢课成功；该目标停止轮询，累计 %d 次", job.target.name, n))
							job.target.cancel()
						}
					} else if isFullResponse(body) {
						if job.round.full.CompareAndSwap(false, true) {
							appendLog(fmt.Sprintf("[%s] 候选 %s 返回满额，切换下一优先级", job.target.name, job.round.name))
							job.round.cancel()
						}
						return
					} else if n == 1 || n%10 == 0 {
						appendLog(fmt.Sprintf("线程 %d：第 %d 次未成功：%s", id, n, compact(body, 120)))
					}
				}
				if interval > 0 && !success.Load() && !job.target.done.Load() && !job.round.full.Load() {
					select {
					case <-ctx.Done():
						return
					case <-time.After(interval + time.Duration(rand.Intn(31))*time.Millisecond):
					}
				}
			}
		}(workerID)
	}
	for i := 0; i < workers; i++ {
		<-done
	}
	if !job.target.done.Load() {
		appendLog(fmt.Sprintf("[%s] 本轮结束：请求 %d 次，超时 %d 次，异常 %d 次", job.target.name, attempts.Load(), timeouts.Load(), failures.Load()))
	}
	return job.target.done.Load()
}

func allTargetsDone(targets []*batchTarget) bool { return remainingTargets(targets) == 0 }
func groupBatchTargets(lines []string) []*batchTarget {
	targets := make([]*batchTarget, 0, len(lines))
	byKey := make(map[string]*batchTarget)
	for _, line := range lines {
		key, display := line, line
		if match := teachingClassNamePattern.FindStringSubmatch(line); len(match) == 4 {
			key = match[1] + "|" + match[2]
			display = match[2]
		}
		target := byKey[key]
		if target == nil {
			targetCtx, cancel := context.WithCancel(context.Background())
			target = &batchTarget{name: display, ctx: targetCtx, cancel: cancel}
			byKey[key] = target
			targets = append(targets, target)
		}
		target.candidates = append(target.candidates, line)
	}
	return targets
}

func (target *batchTarget) nextPriority() int {
	target.mu.Lock()
	defer target.mu.Unlock()
	return target.next
}

func (target *batchTarget) advanceAfter(index int) {
	target.mu.Lock()
	defer target.mu.Unlock()
	if target.next <= index {
		target.next = index + 1
	}
}

func (target *batchTarget) resetPriority() {
	target.mu.Lock()
	target.next = 0
	target.mu.Unlock()
}

func isFullResponse(body string) bool {
	text := strings.ToLower(body)
	markers := []string{"人数已满", "选课人数已满", "教学班已满", "容量已满", "已无余量", "无余量", "余量不足", "名额已满", "course is full"}
	for _, marker := range markers {
		if strings.Contains(text, marker) {
			return true
		}
	}
	return false
}

func remainingTargets(targets []*batchTarget) int {
	count := 0
	for _, target := range targets {
		if !target.done.Load() {
			count++
		}
	}
	return count
}
func waitWhileRunning(running *atomic.Bool, duration time.Duration) {
	deadline := time.Now().Add(duration)
	for running.Load() && time.Now().Before(deadline) {
		time.Sleep(50 * time.Millisecond)
	}
}

func (d *DesktopApp) exportGrades() {
	if len(d.grades) == 0 {
		d.showError("没有可导出的成绩", fmt.Errorf("请先查询成绩"))
		return
	}
	dialog.ShowFileSave(func(writer fyne.URIWriteCloser, err error) {
		if err != nil || writer == nil {
			return
		}
		defer writer.Close()
		csvWriter := csv.NewWriter(writer)
		_ = csvWriter.Write([]string{"学年", "学期", "课程代码", "课程名称", "课程性质", "学分", "成绩", "绩点", "成绩性质", "学位课", "开课学院", "任课教师", "考核方式"})
		for _, grade := range d.grades {
			_ = csvWriter.Write(grade.Row())
		}
		csvWriter.Flush()
		if csvWriter.Error() != nil {
			d.showError("导出失败", csvWriter.Error())
			return
		}
		d.setStatus("成绩已导出")
	}, d.window)
}

func (d *DesktopApp) requireLogin() bool {
	if d.client.LoggedIn() {
		return true
	}
	d.showError("未登录", fmt.Errorf("请先在“登录与会话”中设置 Cookie"))
	return false
}
func (d *DesktopApp) setStatus(text string) { d.status.SetText(text) }
func (d *DesktopApp) showError(title string, err error) {
	dialog.ShowError(err, d.window)
	d.setStatus(title + "：" + err.Error())
}
func academicYears() []string {
	now := time.Now()
	start := now.Year()
	if now.Month() < time.August {
		start--
	}
	out := []string{"全部"}
	for i := 0; i < 12; i++ {
		out = append(out, fmt.Sprintf("%d-%d", start-i, start-i+1))
	}
	return out
}
func yearValue(name string) string {
	if name == "全部" || len(name) < 4 {
		return ""
	}
	return name[:4]
}
func termValue(name string) string {
	return map[string]string{"第一学期": "3", "第二学期": "12", "第三学期": "16"}[name]
}
func findTab(tabs []model.CourseTab, name string) int {
	for i, tab := range tabs {
		if tab.Name == name {
			return i
		}
	}
	return -1
}
func courseRow(c model.CourseItem) []string {
	return []string{c.Kch, c.Kcmc, c.Jxbmc, c.Xf, c.Yxzrs + "/" + c.Jxbrl, c.TeacherNames(), c.Sksj, c.JxbID}
}
func max(a, b int) int {
	if a > b {
		return a
	}
	return b
}
func positiveInt(value string) (int, error) {
	n, err := strconv.Atoi(strings.TrimSpace(value))
	if err != nil || n < 1 {
		return 0, fmt.Errorf("请输入大于 0 的整数")
	}
	return n, nil
}
func positiveDuration(value string, unit time.Duration) (time.Duration, error) {
	n, err := positiveInt(value)
	return time.Duration(n) * unit, err
}
func pollDuration(value string) (time.Duration, error) {
	n, err := strconv.Atoi(strings.TrimSpace(value))
	if err != nil || n == 0 || n < -1 {
		return 0, fmt.Errorf("请输入 -1（无限）或大于 0 的秒数")
	}
	if n == -1 {
		return -1, nil
	}
	return time.Duration(n) * time.Second, nil
}
func durationText(value time.Duration) string {
	if value < 0 {
		return "无限（直到成功或手动停止）"
	}
	return value.String()
}
func nonNegativeDuration(value string, unit time.Duration) (time.Duration, error) {
	n, err := strconv.Atoi(strings.TrimSpace(value))
	if err != nil || n < 0 {
		return 0, fmt.Errorf("请输入不小于 0 的整数")
	}
	return time.Duration(n) * unit, nil
}
func compact(value string, limit int) string {
	value = strings.Join(strings.Fields(value), " ")
	if len([]rune(value)) > limit {
		return string([]rune(value)[:limit]) + "…"
	}
	return value
}

func gradeMetrics(records []model.GradeRecord) (string, string) {
	var sum float64
	var count int
	var weighted, credits float64
	for _, record := range records {
		if score, err := strconv.ParseFloat(record.Score, 64); err == nil {
			sum += score
			count++
		}
		if credit, err := strconv.ParseFloat(record.Credits, 64); err == nil {
			if point, e := strconv.ParseFloat(record.GradePoint, 64); e == nil {
				weighted += credit * point
				credits += credit
			}
		}
	}
	average := "--"
	gpa := "--"
	if count > 0 {
		average = fmt.Sprintf("%.2f", sum/float64(count))
	}
	if credits > 0 {
		gpa = fmt.Sprintf("%.2f", weighted/credits)
	}
	return average, gpa
}

func splitTargets(value string) []string {
	lines := strings.Split(strings.ReplaceAll(value, "\r\n", "\n"), "\n")
	out := make([]string, 0, len(lines))
	for _, line := range lines {
		target := strings.TrimSpace(line)
		if target == "" {
			continue
		}
		out = append(out, target)
	}
	return out
}
