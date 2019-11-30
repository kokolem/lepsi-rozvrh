/*
 Taken from Bakalab <https://github.com/bakalaborg/bakalab>
 Modified by Vít Skalický 2019
*/
package cz.vitskalicky.lepsirozvrh.items;

import android.util.Log;

import org.joda.time.LocalDate;
import org.joda.time.LocalTime;
import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;
import org.simpleframework.xml.core.Commit;

import java.util.List;
import java.util.ListIterator;

@Root(name = "rozvrh", strict = false)
public class Rozvrh {
    public static final String TAG = Rozvrh.class.getSimpleName();

    public Rozvrh() {
        super();
    }

    @Element(required = false)
    private String typ;

    @ElementList(required = false)
    private List<RozvrhHodinaCaption> hodiny;

    @ElementList(required = false)
    private List<RozvrhDen> dny;

    @Element(required = false)
    private String nazevcyklu;

    @Element(required = false)
    private String zkratkacyklu;

    @Commit
    private void onCommit() {
        deleteNullDays();
        deleteNullCaptions();
        deleteRedundantLessons();
    }

    private void deleteNullDays() {
        ListIterator<RozvrhDen> iteratorDen = dny.listIterator(0);
        while (iteratorDen.hasNext()) {
            RozvrhDen den = iteratorDen.next();
            if (den.getHodiny() == null)
                iteratorDen.remove();
        }
    }

    private void deleteNullCaptions() {
        ListIterator<RozvrhHodinaCaption> iteratorCaption = hodiny.listIterator(0);
        while (iteratorCaption.hasNext()) {
            RozvrhHodinaCaption caption = iteratorCaption.next();
            if (caption.getBegintime() == null || caption.getEndtime() == null)
                iteratorCaption.remove();
        }
    }

    private void deleteRedundantLessons() {
        //we also call fixTimes here for each day to assign begintime and endtime
        //TODO: checking for free classes at the beginning of the day in a smart way
        for (RozvrhDen den : dny) {
            den.fixTimes(hodiny);
            List<RozvrhHodina> denHodiny = den.getHodiny();
            ListIterator<RozvrhHodina> i = denHodiny.listIterator(denHodiny.size());
            while (i.hasPrevious()) {
                RozvrhHodina hodina = i.previous();
                if (!(hodina.getHighlight() == RozvrhHodina.EMPTY))
                    break;

                i.remove();
            }
        }
    }

    /**
     * returns the lesson, which should be highlighted to the user as next or current lesson or null
     * if the school is over or this is not the current week.
     */
    public GetAnyLessonReturnValues getRelevantLesson() {
        //LocalDate nowDate = LocalDate.now()
        LocalDate nowDate = LocalDate.parse("2019-12-02");
        LocalTime nowTime = LocalTime.now();

        RozvrhDen dneska = null;
        int denIndex = 0;
        for (RozvrhDen item : dny) {
            if (item.getParsedDatum() == null) //permanent timetable check
                return null;
            if (item.getParsedDatum().isEqual(nowDate)) {
                dneska = item;
                break;
            }
            denIndex++;
        }

        if (dneska == null) //current timetable check
            return null;

        RozvrhHodina dalsi = null;
        int hodinaIndex = 0;
        for (int i = 0; i < dneska.getHodiny().size(); i++) {
            RozvrhHodina item = dneska.getHodiny().get(i);
            if (nowTime.isBefore(item.getParsedEndtime()) && !item.getTyp().equals("X")) {
                dalsi = item;
                break;
            }
            hodinaIndex++;
        }

        if (dalsi == null) {
            denIndex = -1;
            hodinaIndex = -1;
        }

        GetAnyLessonReturnValues ret = new GetAnyLessonReturnValues();
        ret.rozvrhHodina = dalsi;
        ret.dayIndex = denIndex;
        ret.lessonIndex = hodinaIndex;


        return ret;
    }

    /**
     * returns the lesson that is currently running
     * or the one that just ended if it's a break between lessons right now
     * or null if the school is over
     * or null if this is not the current week
     */
    public GetAnyLessonReturnValues getCurrentLesson() {
        //LocalDate nowDate = LocalDate.now()
        LocalDate nowDate = LocalDate.parse("2019-12-02");
        LocalTime nowTime = LocalTime.now();


        RozvrhDen dneska = null;
        int denIndex = 0;
        for (RozvrhDen item : dny) {
            if (item.getParsedDatum() == null) //permanent timetable check
                return null;
            if (item.getParsedDatum().isEqual(nowDate)) {
                dneska = item;
                break;
            }
            denIndex++;
        }

        if (dneska == null) //current timetable check
            return null;

        RozvrhHodina dalsi = null;
        int hodinaIndex = 0;
        for (int i = 0; i < dneska.getHodiny().size(); i++) {
            RozvrhHodina item = dneska.getHodiny().get(i);
            RozvrhHodina nextItem = dneska.getHodiny().get(i+1);
            if (nowTime.isAfter(item.getParsedBegintime()) && nowTime.isBefore(nextItem.getParsedBegintime())) {
                dalsi = item;
                break;
            }
            hodinaIndex++;
        }

        if (dalsi == null) {
            denIndex = -1;
            hodinaIndex = -1;
        }

        GetAnyLessonReturnValues ret = new GetAnyLessonReturnValues();
        ret.rozvrhHodina = dalsi;
        ret.dayIndex = denIndex;
        ret.lessonIndex = hodinaIndex;


        return ret;
    }

    /**
     * returns the next lesson
     * or null if the school is over
     * or null if current lesson is the last one
     * or null if this is not the current week
     */
    public GetAnyLessonReturnValues getNextLesson() {
        GetAnyLessonReturnValues currentLesson = getCurrentLesson();
        RozvrhHodina nextLesson;

        if (currentLesson != null) {
            if (currentLesson.rozvrhHodina == null) nextLesson = null;
        } else {
            nextLesson = null;
        }

        int denIndex = currentLesson.dayIndex;
        int currentLessonIndex = currentLesson.lessonIndex;
        RozvrhDen dneska = dny.get(denIndex);

        try {
            nextLesson = dneska.getHodiny().get(currentLessonIndex+1);
        } catch(IndexOutOfBoundsException e) {
            nextLesson = null;
        }

        int nextLessonIndex;
        if (nextLesson == null) {
            denIndex = -1;
            nextLessonIndex = -1;
        } else {
            nextLessonIndex = currentLessonIndex + 1;
        }

        GetAnyLessonReturnValues ret = new GetAnyLessonReturnValues();
        ret.rozvrhHodina = nextLesson;
        ret.dayIndex = denIndex;
        ret.lessonIndex = nextLessonIndex;

        return ret;
    }


    /**
     * returns the current break or lesson
     * or null
     */
    public BreakOrLesson getCurrentBreakOrLesson() {
        return null;
    }

    /**
     * return values for any get*Lesson() method
     */
    public static class GetAnyLessonReturnValues {
        public RozvrhHodina rozvrhHodina;
        public int dayIndex;
        public int lessonIndex;
    }

    public List<RozvrhHodinaCaption> getHodiny() {
        return hodiny;
    }

    public List<RozvrhDen> getDny() {
        return dny;
    }

    public String getTyp() {
        return typ;
    }

    public String getNazevcyklu() {
        return nazevcyklu;
    }

    public String getZkratkacyklu() {
        return zkratkacyklu;
    }

    /**
     * Return a description of of rozvrh's structure. Used for crash reports.
     * <p>
     * structure:
     * typ; zkratkacyklu;
     * captions count; first caption start time; last caption end time;
     * //for each day one line
     * zkratka; lesson count; first lesson caption; first lesson begin time; last caption; last end time;
     */
    public String getStructure() {
        StringBuilder sb = new StringBuilder();

        try {
            sb.append(typ).append("; ")
                    .append(nazevcyklu).append(";\n")
                    .append(hodiny.size()).append("; ")
                    .append(hodiny.get(0).getBegintime()).append("; ")
                    .append(hodiny.get(hodiny.size() - 1).getEndtime()).append(";\n");
            for (RozvrhDen item : dny) {
                sb.append(item.getZkratka()).append("; ")
                        .append(item.getHodiny().size()).append("; ")
                        .append(item.getHodiny().get(0).getCaption()).append("; ")
                        .append(item.getHodiny().get(0).getBegintime()).append("; ")
                        .append(item.getHodiny().get(item.getHodiny().size() - 1).getCaption()).append("; ")
                        .append(item.getHodiny().get(item.getHodiny().size() - 1).getEndtime()).append(";\n");
            }
        } catch (Exception e) {
            Log.e(TAG, "Creating rozvrh structure failed", e);
        }
        return sb.toString();
    }

    @Override
    public String toString() {
        return "Rozvrh{" +
                "typ='" + typ + '\'' +
                ", hodiny=" + hodiny +
                ", dny=" + dny +
                ", nazevcyklu='" + nazevcyklu + '\'' +
                ", zkratkacyklu='" + zkratkacyklu + '\'' +
                '}';
    }
}
