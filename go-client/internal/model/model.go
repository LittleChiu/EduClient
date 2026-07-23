package model

// GradeRecord is one row returned by the academic-affairs grade endpoint.
type GradeRecord struct {
	AcademicYear     string
	Semester         string
	CourseCode       string
	CourseName       string
	CourseNature     string
	Credits          string
	Score            string
	GradePoint       string
	ScoreNature      string
	DegreeCourse     string
	College          string
	Teacher          string
	AssessmentMethod string
}

func (g GradeRecord) Row() []string {
	return []string{g.AcademicYear, g.Semester, g.CourseCode, g.CourseName, g.CourseNature, g.Credits, g.Score, g.GradePoint, g.ScoreNature, g.DegreeCourse, g.College, g.Teacher, g.AssessmentMethod}
}

// CourseTab describes one selection category shown by the academic-affairs site.
type CourseTab struct {
	Name, Kklxdm, XkkzID, NjdmID, ZyhID, BklxID string
}

// UserInfo contains the session-scoped hidden parameters needed by course APIs.
type UserInfo struct {
	XqhID, JgID, NjdmID, ZyhID, ZyfxID, BhID, Xbm, Xslbdm, Mzm, Xz, Ccdm, Xsbj, NjdmIDXs, ZyhIDXs, Xkxnm, Xkxqm string
}

// CourseContext is parsed from the category display response.
type CourseContext struct {
	Rwlx, Rlkz, Cdrllkz, Rlzlkz, Xklc string
}

// CourseItem is a course or teaching-class item returned by the selection APIs.
type CourseItem struct {
	JxbID, Jxbzls, KchID, Kch, Kcmc, Jxbmc, Xf, Rwzxs, Jxbrl, Jxbrs, Yxzrs, Kklxdm, Jsxx, Sksj, Cxbj, Fxbj, DoJxbID, Xxkbj, Xsdm string
}

func (c CourseItem) TeacherNames() string {
	if c.Jsxx == "" {
		return ""
	}
	return c.Jsxx
}
