package cz.vitskalicky.lepsirozvrh.items;

import org.joda.time.LocalTime;

public class Break {
    private LocalTime beginTime;
    private LocalTime endTime;

    public Break(LocalTime beginTime, LocalTime endTime) {
        this.beginTime = beginTime;
        this.endTime = endTime;
    }

    public LocalTime getBeginTime() {
        return beginTime;
    }

    public LocalTime getEndTime() {
        return endTime;
    }

    @Override
    public String toString() {
        return "Break{" +
                "beginTime=" + beginTime +
                ", endTime=" + endTime +
                '}';
    }
}
