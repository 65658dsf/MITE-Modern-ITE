package io.github.ningmeng123.modernite;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
/** Calendar and budget boundaries from the approved design. @author NingMeng123 */
class ExtremeCalendarTest {
    @Test void eventsAndCounts() {
        assertTrue(ExtremeCalendar.blood(96));assertFalse(ExtremeCalendar.blood(128));assertFalse(ExtremeCalendar.blood(143));assertTrue(ExtremeCalendar.blood(144));assertTrue(ExtremeCalendar.blood(256));
        assertEquals(3,ExtremeCalendar.bloodCount(128));assertEquals(4,ExtremeCalendar.bloodCount(144));assertEquals(8,ExtremeCalendar.bloodCount(208));assertEquals(9,ExtremeCalendar.bloodCount(224));
        for(int day=1;day<3000;day++){long count=0;for(int i=1;i<=day;i++)if(ExtremeCalendar.blood(i))count++;assertEquals(count,ExtremeCalendar.bloodCount(day));}
    }
    @Test void separateHeightBudgets() { assertEquals(212,ExtremeCalendar.cap(1,289,false,false));assertEquals(636,ExtremeCalendar.cap(32,289,true,false));assertEquals(1272,ExtremeCalendar.cap(32,289,true,true));assertEquals(1112,ExtremeCalendar.cap(1000,289,false,false)); }
}
