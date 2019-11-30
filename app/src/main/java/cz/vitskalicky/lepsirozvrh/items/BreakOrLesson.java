package cz.vitskalicky.lepsirozvrh.items;

import org.joda.time.LocalTime;

public class BreakOrLesson {
    private Break currentBreak;
    private RozvrhHodina currentLesson;

    public BreakOrLesson(RozvrhHodina currentLesson, RozvrhHodina nextLesson) {
        LocalTime nowTime = LocalTime.now();
        if (nowTime.isBefore(currentLesson.getParsedEndtime())) {
            this.currentLesson = currentLesson;
        } else {
            this.currentBreak = new Break(currentLesson.getParsedEndtime(), nextLesson.getParsedBegintime());
        }
    }

    public BreakOrLesson(RozvrhHodina currentLesson) {
        this.currentLesson = currentLesson;
    }

    public Break getCurrentBreak() {
        return currentBreak;
    }

    public RozvrhHodina getCurrentLesson() {
        return currentLesson;
    }
}
