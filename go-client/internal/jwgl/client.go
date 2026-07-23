package jwgl

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/LittleChiu/EduClient/go-client/internal/model"
)

const (
	defaultBaseURL = "https://jwglxt.zstu.edu.cn"
	userAgent      = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/138.0.0.0 Safari/537.36 Edge/138.0.0.0"
)

var courseTabPattern = regexp.MustCompile(`(?s)queryCourse\(this,'(\d+?)','(.*?)','(\d+?)','(\d+?)'\).*?>(.*?)<`)
var hiddenInputPattern = regexp.MustCompile(`(?is)<input[^>]+(?:name|id)\s*=\s*["']([^"']+)["'][^>]*>`) // used only to find candidate elements
var attrPattern = regexp.MustCompile(`(?is)([\w-]+)\s*=\s*["']([^"']*)["']`)

// Client owns the authenticated session and all requests to jwglxt.
type Client struct {
	mu       sync.RWMutex
	baseURL  string
	cookie   string
	http     *http.Client
	userInfo model.UserInfo
	context  model.CourseContext
}

// cancelOnClose keeps the request deadline active while callers read a response
// body, then releases its timer as soon as the body is closed.
type cancelOnClose struct {
	io.ReadCloser
	cancel context.CancelFunc
	once   sync.Once
}

func (body *cancelOnClose) Close() error {
	err := body.ReadCloser.Close()
	body.once.Do(body.cancel)
	return err
}

func NewClient() *Client {
	return &Client{baseURL: defaultBaseURL, http: &http.Client{Timeout: 15 * time.Second}}
}

// SetSession atomically switches the server origin and its matching cookies.
// Cookies issued by the direct and WebVPN hosts must never be mixed.
func (c *Client) SetSession(baseURL, cookie string) {
	c.mu.Lock()
	c.baseURL = strings.TrimRight(strings.TrimSpace(baseURL), "/")
	if c.baseURL == "" {
		c.baseURL = defaultBaseURL
	}
	c.cookie = strings.TrimSpace(cookie)
	c.userInfo = model.UserInfo{}
	c.context = model.CourseContext{}
	c.mu.Unlock()
}

func (c *Client) SetCookie(cookie string) {
	c.mu.Lock()
	c.cookie = strings.TrimSpace(cookie)
	c.mu.Unlock()
}
func (c *Client) Cookie() string  { c.mu.RLock(); defer c.mu.RUnlock(); return c.cookie }
func (c *Client) BaseURL() string { c.mu.RLock(); defer c.mu.RUnlock(); return c.baseURL }
func (c *Client) LoggedIn() bool  { return c.Cookie() != "" }

func (c *Client) session() (string, string) {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.baseURL, c.cookie
}

func (c *Client) ValidateSession(ctx context.Context) error {
	response, err := c.do(ctx, http.MethodGet, "/jwglxt/xtgl/index_initMenu.html?jsdm=xs", nil, 8*time.Second)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("会话校验返回 HTTP %d", response.StatusCode)
	}
	body, err := io.ReadAll(response.Body)
	if err != nil {
		return err
	}
	if !strings.Contains(response.Request.URL.Path, "/jwglxt/xtgl/index_initMenu.html") || strings.Contains(strings.ToLower(string(body)), "jasiglogin") {
		return errors.New("登录状态已失效")
	}
	return nil
}

func (c *Client) FetchGrades(ctx context.Context, year, semester string) ([]model.GradeRecord, error) {
	form := url.Values{
		"xnm": {year}, "xqm": {semester}, "_search": {"false"}, "nd": {strconv.FormatInt(time.Now().UnixMilli(), 10)},
		"queryModel.showCount": {"5000"}, "queryModel.currentPage": {"1"}, "queryModel.sortName": {"xnm,xqm,kch"}, "queryModel.sortOrder": {"desc"}, "time": {"1"},
	}
	response, err := c.doWithHeaders(ctx, http.MethodPost, "/jwglxt/cjcx/cjcx_cxDgXscj.html?doType=query&gnmkdm=N305005", form, 12*time.Second, http.Header{
		"Referer": {c.BaseURL() + "/jwglxt/cjcx/cjcx_cxDgXscj.html?gnmkdm=N305005&layout=default"},
	})
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	body, err := io.ReadAll(response.Body)
	if err != nil {
		return nil, err
	}
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("成绩查询返回 HTTP %d", response.StatusCode)
	}
	if strings.HasPrefix(strings.TrimSpace(string(body)), "<") {
		return nil, errors.New("登录状态已失效")
	}
	var payload struct {
		Items []map[string]any `json:"items"`
	}
	if err := json.Unmarshal(body, &payload); err != nil {
		return nil, fmt.Errorf("成绩数据解析失败: %w", err)
	}
	if len(payload.Items) == 0 && strings.Contains(string(body), "901") {
		return nil, errors.New("成绩接口返回 901：会话 Cookie 与成绩页请求上下文不匹配")
	}
	records := make([]model.GradeRecord, 0, len(payload.Items))
	for _, item := range payload.Items {
		records = append(records, model.GradeRecord{
			AcademicYear: text(item, "xnmmc", "xnm"), Semester: text(item, "xqmmc", "xqm"), CourseCode: text(item, "kch", "kch_id"), CourseName: text(item, "kcmc"),
			CourseNature: text(item, "kcxzmc"), Credits: text(item, "xf"), Score: text(item, "cj", "bfzcj"), GradePoint: text(item, "jd"), ScoreNature: text(item, "cjxzmc"),
			DegreeCourse: text(item, "sfxwkc"), College: text(item, "kkbmmc", "kkxy"), Teacher: text(item, "jsxm"), AssessmentMethod: text(item, "khfsmc"),
		})
	}
	return records, nil
}

func (c *Client) FetchCourseTabs(ctx context.Context) ([]model.CourseTab, error) {
	response, err := c.do(ctx, http.MethodGet, "/jwglxt/xsxk/zzxkyzb_cxZzxkYzbIndex.html?gnmkdm=N253512&layout=default", nil, 12*time.Second)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	body, err := io.ReadAll(response.Body)
	if err != nil {
		return nil, err
	}
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("选课页返回 HTTP %d", response.StatusCode)
	}
	html := string(body)
	c.mu.Lock()
	c.userInfo = parseUserInfo(html)
	c.mu.Unlock()
	matches := courseTabPattern.FindAllStringSubmatch(html, -1)
	tabs := make([]model.CourseTab, 0, len(matches))
	for _, m := range matches {
		tabs = append(tabs, model.CourseTab{Name: stripHTML(m[5]), Kklxdm: m[1], XkkzID: m[2], NjdmID: m[3], ZyhID: m[4]})
	}
	if len(tabs) == 0 && strings.Contains(html, "iskxk=0") {
		return nil, errors.New("当前不在选课开放时间，页面未提供课程分类")
	}
	return tabs, nil
}

func (c *Client) FetchCourses(ctx context.Context, tab model.CourseTab, keyword string) ([]model.CourseItem, error) {
	return c.FetchCoursesForTab(ctx, &tab, keyword)
}

// FetchCoursesForTab updates dynamic metadata on tab and fetches the basic
// course list, matching the Java batch workflow.
func (c *Client) FetchCoursesForTab(ctx context.Context, tab *model.CourseTab, keyword string) ([]model.CourseItem, error) {
	if tab.BklxID == "" {
		if err := c.FetchCourseMetadata(ctx, tab); err != nil {
			return nil, err
		}
	}
	u := c.user()
	form := url.Values{"filter_list[0]": {keyword}, "xqh_id": {u.XqhID}, "jg_id": {u.JgID}, "njdm_id": {u.NjdmID}, "zyh_id": {u.ZyhID}, "xkxnm": {u.Xkxnm}, "xkxqm": {u.Xkxqm}, "kklxdm": {tab.Kklxdm}, "xkkz_id": {tab.XkkzID}, "kspage": {"1"}, "jspage": {"100"}}
	response, err := c.do(ctx, http.MethodPost, "/jwglxt/xsxk/zzxkyzb_cxZzxkYzbPartDisplay.html?gnmkdm=N253512", form, 12*time.Second)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	body, err := io.ReadAll(response.Body)
	if err != nil {
		return nil, err
	}
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("课程查询返回 HTTP %d", response.StatusCode)
	}
	var payload struct {
		TmpList []courseDTO `json:"tmpList"`
	}
	if strings.TrimSpace(string(body)) == "0" || strings.TrimSpace(string(body)) == "\"0\"" {
		return []model.CourseItem{}, nil
	}
	if err := json.Unmarshal(body, &payload); err != nil {
		return nil, fmt.Errorf("课程数据解析失败: %w", err)
	}
	items := make([]model.CourseItem, 0, len(payload.TmpList))
	for _, item := range payload.TmpList {
		items = append(items, item.toModel())
	}
	return items, nil
}

// FetchCourseDetails resolves a basic course result into the actual teaching
// classes. Its form fields and merge rules mirror EduService.fetchCourseDetails.
func (c *Client) FetchCourseDetails(ctx context.Context, tab model.CourseTab, keyword, kchID string, contextItems []model.CourseItem) ([]model.CourseItem, error) {
	u := c.user()
	representative := model.CourseItem{}
	if len(contextItems) > 0 {
		representative = contextItems[0]
	}
	form := url.Values{
		"filter_list[0]": {keyword}, "xqh_id": {u.XqhID}, "jg_id": {u.JgID}, "njdm_id": {u.NjdmID}, "zyh_id": {u.ZyhID},
		"njdm_id_1": {u.NjdmID}, "zyh_id_1": {u.ZyhID}, "xkxnm": {u.Xkxnm}, "xkxqm": {u.Xkxqm},
		"kklxdm": {tab.Kklxdm}, "xkkz_id": {tab.XkkzID}, "kch_id": {kchID}, "rwlx": {"1"}, "xkly": {"1"},
		"bklx_id": {or(tab.BklxID, "0")}, "sfkkjyxdxnxq": {"0"}, "kzkcgs": {"0"}, "zyfx_id": {u.ZyfxID},
		"njdm_id_xs": {u.NjdmIDXs}, "bh_id": {u.BhID}, "xbm": {u.Xbm}, "xslbdm": {u.Xslbdm}, "mzm": {u.Mzm},
		"xz": {u.Xz}, "ccdm": {u.Ccdm}, "xsbj": {u.Xsbj}, "sfkknj": {"1"}, "sfkkzy": {"1"}, "kzybkxy": {"0"},
		"sfznkx": {"0"}, "zdkxms": {"0"}, "sfkxq": {"0"}, "sfkcfx": {"0"}, "bbhzxjxb": {"0"}, "kkbk": {"0"},
		"kkbkdj": {"0"}, "bklbkcj": {"0"}, "xkxskcgskg": {"0"}, "rlkz": {"0"}, "cdrlkz": {"0"}, "rlzlkz": {"0"},
		"jxbzcxskg": {"0"}, "xklc": {"1"}, "cxbj": {or(representative.Cxbj, "0")}, "fxbj": {or(representative.Fxbj, "0")}, "txbsfrl": {"0"},
	}
	response, err := c.do(ctx, http.MethodPost, "/jwglxt/xsxk/zzxkyzbjk_cxJxbWithKchZzxkYzb.html?gnmkdm=N253512", form, 15*time.Second)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	body, err := io.ReadAll(response.Body)
	if err != nil {
		return nil, err
	}
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("教学班详情返回 HTTP %d", response.StatusCode)
	}
	trimmed := strings.TrimSpace(string(body))
	if trimmed == "0" || trimmed == "\"0\"" {
		return []model.CourseItem{}, nil
	}
	var raw []courseDTO
	if err := json.Unmarshal(body, &raw); err != nil {
		return nil, fmt.Errorf("教学班详情解析失败: %w", err)
	}
	details := make([]model.CourseItem, len(raw))
	contextMap := make(map[string]model.CourseItem, len(contextItems))
	for _, item := range contextItems {
		if item.JxbID != "" {
			contextMap[item.JxbID] = item
		}
	}
	for i, dto := range raw {
		detail := dto.toModel()
		if source, ok := contextMap[detail.JxbID]; ok {
			detail.Cxbj = or(source.Cxbj, "0")
			detail.Fxbj = or(source.Fxbj, "0")
			detail.Xxkbj = or(source.Xxkbj, "0")
			if detail.Kcmc == "" {
				detail.Kcmc = source.Kcmc
			}
			if detail.Kch == "" {
				detail.Kch = source.Kch
			}
			if detail.Xf == "" {
				detail.Xf = source.Xf
			}
			if detail.Kklxdm == "" {
				detail.Kklxdm = source.Kklxdm
			}
			if detail.Jxbmc == "" {
				detail.Jxbmc = source.Jxbmc
			}
			if detail.KchID == "" {
				detail.KchID = kchID
			}
			if detail.Jxbzls == "" {
				detail.Jxbzls = source.Jxbzls
			}
		}
		details[i] = detail
	}
	return details, nil
}

// FetchChildClasses mirrors EduService.fetchChildClasses for linked lecture,
// lab and other multi-part teaching classes.
func (c *Client) FetchChildClasses(ctx context.Context, tab model.CourseTab, parent model.CourseItem) ([]model.CourseItem, error) {
	u, state := c.user(), c.courseContext()
	form := url.Values{
		"xkxnm": {u.Xkxnm}, "xkxqm": {u.Xkxqm}, "xkly": {"1"}, "jxb_id": {parent.DoJxbID}, "jxbzls": {"2"},
		"cdrlkz": {or(state.Cdrllkz, "0")}, "rlkz": {or(state.Rlkz, "0")}, "rlzlkz": {or(state.Rlzlkz, "0")},
		"rwlx": {or(state.Rwlx, "1")}, "syqz": {"100"}, "zyfx_id": {u.ZyfxID}, "bh_id": {u.BhID}, "zyh_id": {u.ZyhID},
		"txbsfrl": {"0"}, "njdm_id": {u.NjdmID}, "sfkknj": {"1"}, "gnjkxdnj": {"0"}, "sfkkzy": {"1"}, "zh": {""},
		"sfznkx": {"0"}, "kklxdm": {tab.Kklxdm}, "bklx_id": {or(tab.BklxID, "0")}, "xklc": {state.Xklc},
		"kkbk": {"0"}, "kkbkdj": {"0"}, "bklbkcj": {"0"}, "fxbj": {or(parent.Fxbj, "0")}, "cxbj": {or(parent.Cxbj, "0")},
		"kzybkxy": {"0"}, "zcongbj": {"0"},
	}
	response, err := c.do(ctx, http.MethodPost, "/jwglxt/xsxk/zzxkyzb_xkZyDisplayZzxkYzbZjxb.html?gnmkdm=N253512", form, 15*time.Second)
	if err != nil {
		return nil, err
	}
	defer response.Body.Close()
	body, err := io.ReadAll(response.Body)
	if err != nil {
		return nil, err
	}
	if response.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("关联教学班返回 HTTP %d", response.StatusCode)
	}
	if !strings.HasPrefix(strings.TrimSpace(string(body)), "[") {
		return []model.CourseItem{}, nil
	}
	var raw []courseDTO
	if err := json.Unmarshal(body, &raw); err != nil {
		return nil, fmt.Errorf("关联教学班解析失败: %w", err)
	}
	items := make([]model.CourseItem, len(raw))
	for i, dto := range raw {
		items[i] = dto.toModel()
	}
	return items, nil
}

// SmartJxbIDs applies the Java getSmartJxbIds grouping rule.
func (c *Client) SmartJxbIDs(ctx context.Context, tab model.CourseTab, detail model.CourseItem) (string, error) {
	zls, _ := strconv.Atoi(strings.TrimSpace(detail.Jxbzls))
	if zls != 2 {
		return "", nil
	}
	children, err := c.FetchChildClasses(ctx, tab, detail)
	if err != nil {
		return "", err
	}
	distinct := make(map[string]string)
	for _, child := range children {
		if child.Xsdm != "" {
			if _, exists := distinct[child.Xsdm]; !exists {
				distinct[child.Xsdm] = child.DoJxbID
			}
		}
	}
	if len(distinct) <= 1 {
		return "", nil
	}
	ids := make([]string, 0, len(distinct))
	for _, id := range distinct {
		ids = append(ids, id)
	}
	return strings.Join(ids, ","), nil
}

func (c *Client) FetchCourseMetadata(ctx context.Context, tab *model.CourseTab) error {
	form := url.Values{"xkkz_id": {tab.XkkzID}, "xszxzt": {"1"}, "kklxdm": {tab.Kklxdm}, "njdm_id": {tab.NjdmID}, "zyh_id": {tab.ZyhID}, "kspage": {"0"}, "jspage": {"0"}}
	response, err := c.do(ctx, http.MethodPost, "/jwglxt/xsxk/zzxkyzb_cxZzxkYzbDisplay.html?gnmkdm=N253512", form, 12*time.Second)
	if err != nil {
		return err
	}
	defer response.Body.Close()
	body, err := io.ReadAll(response.Body)
	if err != nil {
		return err
	}
	if response.StatusCode != http.StatusOK {
		return fmt.Errorf("选课初始化返回 HTTP %d", response.StatusCode)
	}
	fields := parseInputs(string(body))
	tab.BklxID = fields["bklx_id"]
	c.mu.Lock()
	c.context = model.CourseContext{Rwlx: fields["rwlx"], Rlkz: fields["rlkz"], Cdrllkz: fields["cdrlkz"], Rlzlkz: fields["rlzlkz"], Xklc: fields["xklc"]}
	c.mu.Unlock()
	return nil
}

// Enroll sends one teaching-class request. It is intentionally separate from the UI so callers can choose their own scheduling policy.
func (c *Client) Enroll(ctx context.Context, tab model.CourseTab, course model.CourseItem, timeout time.Duration) (bool, string, error) {
	return c.EnrollWithIDs(ctx, tab, course, course.DoJxbID, timeout)
}

// EnrollWithIDs mirrors the Java overload used for linked teaching classes.
func (c *Client) EnrollWithIDs(ctx context.Context, tab model.CourseTab, course model.CourseItem, jxbIDs string, timeout time.Duration) (bool, string, error) {
	return c.EnrollWithCourseContext(ctx, tab, course, jxbIDs, c.courseContext(), timeout)
}

// CurrentCourseContext returns the dynamic hidden-field context captured for
// the most recently prepared course tab.
func (c *Client) CurrentCourseContext() model.CourseContext {
	return c.courseContext()
}

// EnrollWithCourseContext prevents concurrent batch targets from overwriting
// each other's tab-specific enrollment context.
func (c *Client) EnrollWithCourseContext(ctx context.Context, tab model.CourseTab, course model.CourseItem, jxbIDs string, state model.CourseContext, timeout time.Duration) (bool, string, error) {
	u := c.user()
	sxbj := "0"
	if state.Rlkz == "1" || state.Cdrllkz == "1" || state.Rlzlkz == "1" {
		sxbj = "1"
	}
	form := url.Values{"jxb_ids": {jxbIDs}, "kch_id": {course.KchID}, "kcmc": {course.Kcmc}, "rwlx": {or(state.Rwlx, "1")}, "rlkz": {or(state.Rlkz, "0")}, "cdrlkz": {or(state.Cdrllkz, "0")}, "rlzlkz": {or(state.Rlzlkz, "0")}, "sxbj": {sxbj}, "xxkbj": {or(course.Xxkbj, "0")}, "qz": {"0"}, "cxbj": {or(course.Cxbj, "0")}, "xkkz_id": {tab.XkkzID}, "njdm_id": {tab.NjdmID}, "zyh_id": {tab.ZyhID}, "kklxdm": {tab.Kklxdm}, "xklc": {state.Xklc}, "xkxnm": {u.Xkxnm}, "xkxqm": {u.Xkxqm}, "jcxx_id": {""}}
	response, err := c.do(ctx, http.MethodPost, "/jwglxt/xsxk/zzxkyzbjk_xkBcZyZzxkYzb.html?gnmkdm=N253512", form, timeout)
	if err != nil {
		return false, "", err
	}
	defer response.Body.Close()
	body, err := io.ReadAll(response.Body)
	if err != nil {
		return false, "", err
	}
	if response.StatusCode != http.StatusOK {
		return false, string(body), fmt.Errorf("选课提交返回 HTTP %d", response.StatusCode)
	}
	return strings.Contains(string(body), `"flag":"1"`), string(body), nil
}

func (c *Client) do(ctx context.Context, method, path string, form url.Values, timeout time.Duration) (*http.Response, error) {
	return c.doWithHeaders(ctx, method, path, form, timeout, nil)
}

func (c *Client) doWithHeaders(ctx context.Context, method, path string, form url.Values, timeout time.Duration, headers http.Header) (*http.Response, error) {
	requestBaseURL, requestCookie := c.session()
	if requestCookie == "" {
		return nil, errors.New("请先填写有效 Cookie")
	}
	if timeout <= 0 {
		timeout = 15 * time.Second
	}
	requestCtx, cancel := context.WithTimeout(ctx, timeout)
	var body io.Reader
	if form != nil {
		body = strings.NewReader(form.Encode())
	}
	req, err := http.NewRequestWithContext(requestCtx, method, requestBaseURL+path, body)
	if err != nil {
		cancel()
		return nil, err
	}
	req.Header.Set("Cookie", requestCookie)
	req.Header.Set("User-Agent", userAgent)
	if form != nil {
		req.Header.Set("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
		req.Header.Set("X-Requested-With", "XMLHttpRequest")
	}
	for name, values := range headers {
		for _, value := range values {
			req.Header.Add(name, value)
		}
	}
	response, err := c.http.Do(req)
	if err != nil {
		cancel()
		return nil, err
	}
	response.Body = &cancelOnClose{ReadCloser: response.Body, cancel: cancel}
	return response, nil
}

func (c *Client) user() model.UserInfo { c.mu.RLock(); defer c.mu.RUnlock(); return c.userInfo }
func (c *Client) courseContext() model.CourseContext {
	c.mu.RLock()
	defer c.mu.RUnlock()
	return c.context
}
func text(row map[string]any, keys ...string) string {
	for _, key := range keys {
		if value, ok := row[key]; ok && value != nil {
			return fmt.Sprint(value)
		}
	}
	return ""
}
func or(value, fallback string) string {
	if value == "" {
		return fallback
	}
	return value
}

func parseUserInfo(html string) model.UserInfo {
	f := parseInputs(html)
	return model.UserInfo{XqhID: f["xqh_id"], JgID: f["jg_id_1"], NjdmID: f["njdm_id"], ZyhID: f["zyh_id"], ZyfxID: f["zyfx_id"], BhID: f["bh_id"], Xbm: f["xbm"], Xslbdm: f["xslbdm"], Mzm: f["mzm"], Xz: f["xz"], Ccdm: f["ccdm"], Xsbj: f["xsbj"], NjdmIDXs: f["njdm_id_xs"], ZyhIDXs: f["zyh_id_xs"], Xkxnm: f["xkxnm"], Xkxqm: f["xkxqm"]}
}
func parseInputs(html string) map[string]string {
	fields := make(map[string]string)
	for _, input := range hiddenInputPattern.FindAllString(html, -1) {
		attrs := map[string]string{}
		for _, a := range attrPattern.FindAllStringSubmatch(input, -1) {
			attrs[strings.ToLower(a[1])] = a[2]
		}
		key := attrs["name"]
		if key == "" {
			key = attrs["id"]
		}
		if key != "" {
			fields[key] = attrs["value"]
		}
	}
	return fields
}
func stripHTML(value string) string {
	return strings.TrimSpace(regexp.MustCompile(`<[^>]+>`).ReplaceAllString(value, ""))
}

type courseDTO struct {
	JxbID   string `json:"jxb_id"`
	Jxbzls  string `json:"jxbzls"`
	KchID   string `json:"kch_id"`
	Kch     string `json:"kch"`
	Kcmc    string `json:"kcmc"`
	Jxbmc   string `json:"jxbmc"`
	Xf      string `json:"xf"`
	Rwzxs   string `json:"rwzxs"`
	Jxbrl   string `json:"jxbrl"`
	Jxbrs   string `json:"jxbrs"`
	Yxzrs   string `json:"yxzrs"`
	Kklxdm  string `json:"kklxdm"`
	Jsxx    string `json:"jsxx"`
	Sksj    string `json:"sksj"`
	Cxbj    string `json:"cxbj"`
	Fxbj    string `json:"fxbj"`
	DoJxbID string `json:"do_jxb_id"`
	Xxkbj   string `json:"xxkbj"`
	Xsdm    string `json:"xsdm"`
}

func (d courseDTO) toModel() model.CourseItem {
	return model.CourseItem{JxbID: d.JxbID, Jxbzls: d.Jxbzls, KchID: d.KchID, Kch: d.Kch, Kcmc: d.Kcmc, Jxbmc: d.Jxbmc, Xf: d.Xf, Rwzxs: d.Rwzxs, Jxbrl: d.Jxbrl, Jxbrs: d.Jxbrs, Yxzrs: d.Yxzrs, Kklxdm: d.Kklxdm, Jsxx: d.Jsxx, Sksj: d.Sksj, Cxbj: d.Cxbj, Fxbj: d.Fxbj, DoJxbID: d.DoJxbID, Xxkbj: d.Xxkbj, Xsdm: d.Xsdm}
}
