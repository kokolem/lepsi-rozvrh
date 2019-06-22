/*
 Taken from Bakalab <https://github.com/bakalaborg/bakalab>
 Modified by Vít Skalický 2019
*/
package cz.vitskalicky.lepsirozvrh.bakalab.items;

import org.simpleframework.xml.Element;
import org.simpleframework.xml.ElementList;
import org.simpleframework.xml.Root;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

import cz.vitskalicky.lepsirozvrh.Utils;

@Root(name = "den", strict = false)
public class RozvrhDen {

    public RozvrhDen() {
        super();
    }

    @Element(required = false)
    private String zkratka;

    @Element(required = false)
    private String datum;

    @ElementList(required = false)
    private List<RozvrhHodina> hodiny;

    public String getZkratka() {
        return zkratka;
    }

    public String getDatum() {
        return datum;
    }

    public String getDay() { return Utils.parseDate(datum, "yyyyMMdd", "d"); }

    public List<RozvrhHodina> getHodiny() { return hodiny; }

    public void fixTimes(List<RozvrhHodinaCaption> captionsList) {
        int position = 0;
        for(RozvrhHodina hodina : hodiny){
            RozvrhHodinaCaption mRozvrhHodinaCaption = captionsList.get(position);
            hodina.setBegintime(mRozvrhHodinaCaption.getBegintime());
            hodina.setEndtime(mRozvrhHodinaCaption.getEndtime());
            position++;
        }
    }

    public int getCurrentLessonInt(){
        String currentTime = new SimpleDateFormat("H:m", Locale.US).format(new Date());
        ListIterator<RozvrhHodina> i = hodiny.listIterator();
        while (i.hasNext()) {
            if (Utils.minutesOfDay(i.next().getBegintime()) > Utils.minutesOfDay(currentTime))
                break;
        }
        return i.nextIndex();
    }
}
