package me.yeoc.educlient.object;

import java.util.List;

public record Course(
        String codeTitle,
        double credit,
        List<CourseItem> classes
) {
    public int classCount() { return classes.size(); }
}
